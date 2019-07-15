package cn.soul.android.plugin.component.tasks

import cn.soul.android.plugin.component.PluginArtifactsHolder
import cn.soul.android.plugin.component.PluginVariantScope
import com.android.annotations.VisibleForTesting
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.api.artifact.singleFile
import com.android.build.gradle.internal.incremental.InstantRunBuildContext
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.PROCESSED_JAR
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.ANNOTATION_PROCESSOR
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.ANNOTATION_PROCESSOR_LIST
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.factory.AbstractCompilesUtil
import com.android.builder.profile.ProcessProfileWriter
import com.android.sdklib.AndroidTargetHash
import com.android.utils.FileUtils
import com.android.utils.ILogger
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.wireless.android.sdk.stats.AnnotationProcessorInfo
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.*
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.UncheckedIOException

/**
 * Created by nebula on 2019-07-14
 */
open class AndroidJavaCompile : JavaCompile() {
    private var compileSdkVersion: String? = null
    private var mInstantRunBuildContext: InstantRunBuildContext? = null
    private var annotationProcessorOutputFolder: File? = null

    private var processorListFile: BuildableArtifact? = null

    private var variantName: String? = null

    @PathSensitive(PathSensitivity.NONE)
    @InputFiles
    fun getProcessorListFile(): BuildableArtifact? {
        return processorListFile
    }

    @OutputDirectory
    fun getAnnotationProcessorOutputFolder(): File? {
        return annotationProcessorOutputFolder
    }

    override fun compile(inputs: IncrementalTaskInputs) {
        logger.info(
                "Compiling with source level {} and target level {}.",
                sourceCompatibility,
                targetCompatibility)
        if (isPostN()) {
            if (!JavaVersion.current().isJava8Compatible) {
                throw RuntimeException("compileSdkVersion '" + compileSdkVersion + "' requires "
                        + "JDK 1.8 or later to compile.")
            }
        }

        processAnalytics()

        // Create directory for output of annotation processor.
        FileUtils.mkdirs(annotationProcessorOutputFolder!!)

        super.compile(inputs)
    }

    /** Read the processorListFile to add annotation processors used to analytics.  */
    @VisibleForTesting
    internal fun processAnalytics() {
        val gson = GsonBuilder().create()
        var classNames: List<String>? = null
        try {
            FileReader(processorListFile?.singleFile()).use { reader ->
                classNames = gson.fromJson(reader, object : TypeToken<List<String>>() {

                }.type)
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }

        val projectPath = project.path
        val variant = ProcessProfileWriter.getOrCreateVariant(projectPath, variantName)
        if (classNames == null) {
            return
        }
        for (processorName in classNames!!) {
            val builder = AnnotationProcessorInfo.newBuilder()
            builder.spec = processorName
            variant.addAnnotationProcessors(builder)
        }
    }

    @Internal
    private fun isPostN(): Boolean {
        val hash = AndroidTargetHash.getVersionFromHash(compileSdkVersion!!)
        return hash != null && hash.apiLevel >= 24
    }

    class JavaCompileConfigAction(val scope: PluginVariantScope) : TaskConfigAction<AndroidJavaCompile> {
        private val LOG = LoggerWrapper.getLogger(JavaCompileConfigAction::class.java)

        override fun getName(): String {
            return scope.getTaskName("compile", "JavaWithJavac")
        }

        override fun getType(): Class<AndroidJavaCompile> {
            return AndroidJavaCompile::class.java
        }

        override fun execute(javacTask: AndroidJavaCompile) {
            val globalScope = scope.getGlobalScope()
            val project = globalScope.project
            val artifacts = scope.getArtifacts()
            val isDataBindingEnabled = globalScope.extension.dataBinding.isEnabled

            javacTask.compileSdkVersion = globalScope.extension.compileSdkVersion

            // We can't just pass the collection directly, as the instanceof check in the incremental
            // compile doesn't work recursively currently, so every ConfigurableFileTree needs to be
            // directly in the source array.
            for (fileTree in scope.getVariantData().javaSources) {
//                println("source file tree: ${fileTree.dir}")
                javacTask.source(fileTree)
            }

            javacTask.options.bootstrapClasspath = scope.getBootClasspath()
//            scope.getBootClasspath().files.forEach {
//                println("boot class path: ${it.absolutePath}")
//            }

            var classpath = scope.getJavaClasspath(COMPILE_CLASSPATH, CLASSES)
//            classpath.files.forEach {
//                println("class path: ${it.absolutePath}")
//            }
            if (!globalScope.projectOptions.get(BooleanOption.ENABLE_CORE_LAMBDA_STUBS) && scope.keepDefaultBootstrap()) {
                // adding android.jar to classpath, as it is not in the bootclasspath
                classpath = classpath.plus(
                        project.files(globalScope.androidBuilder.getBootClasspath(false)))
            }
            javacTask.classpath = classpath

            javacTask.destinationDir = PluginArtifactsHolder.appendArtifact(scope,
                    InternalArtifactType.JAVAC,
                    javacTask,
                    "classes")

            val compileOptions = globalScope.extension.compileOptions

            AbstractCompilesUtil.configureLanguageLevel(
                    javacTask,
                    compileOptions,
                    globalScope.extension.compileSdkVersion,
                    scope.getJava8LangSupportType())
            javacTask.options.encoding = compileOptions.encoding

            val includeCompileClasspath = scope.getVariantConfiguration()
                    .javaCompileOptions
                    .annotationProcessorOptions
                    .includeCompileClasspath

            var processorPath = scope.getArtifactFileCollection(ANNOTATION_PROCESSOR, ALL, PROCESSED_JAR)
//            processorPath.files.forEach {
//                println("processorPath: ${it.absolutePath}")
//            }
            if (java.lang.Boolean.TRUE == includeCompileClasspath) {
                // We need the jar files because annotation processors require the resources.
                processorPath = processorPath.plus(scope.getJavaClasspath(COMPILE_CLASSPATH, PROCESSED_JAR))
            }

            javacTask.options.annotationProcessorPath = processorPath

            val incremental = isIncremental(
                    project,
                    scope,
                    compileOptions,
                    null, /* processorConfiguration, JavaCompile handles annotation processor now */
                    LOG)

            if (incremental) {
                LOG.verbose("Using incremental javac compilation for %1\$s %2\$s.",
                        project.path, scope.getFullName())
                javacTask.options.isIncremental = true
            } else {
                LOG.verbose("Not using incremental javac compilation for %1\$s %2\$s.",
                        project.path, scope.getFullName())
            }

            val annotationProcessorOptions = scope.getVariantConfiguration()
                    .javaCompileOptions
                    .annotationProcessorOptions

            if (annotationProcessorOptions.classNames.isNotEmpty()) {
                javacTask.options.compilerArgs.add("-processor")
                javacTask.options.compilerArgs.add(
                        Joiner.on(',').join(annotationProcessorOptions.classNames))
            }
            if (annotationProcessorOptions.arguments.isNotEmpty()) {
                for ((key, value) in annotationProcessorOptions.arguments) {
                    javacTask.options.compilerArgs.add(
                            "-A$key=$value")
                }
            }
            javacTask
                    .options
                    .compilerArgumentProviders
                    .addAll(annotationProcessorOptions.compilerArgumentProviders)

            javacTask
                    .options.annotationProcessorGeneratedSourcesDirectory = scope.getAnnotationProcessorOutputDir()
            javacTask.annotationProcessorOutputFolder = scope.getAnnotationProcessorOutputDir()
//            println("annotation processDir:${scope.getAnnotationProcessorOutputDir()}")

            if (isDataBindingEnabled) {
                // The data binding artifact is created through annotation processing, which is invoked
                // by the JavaCompile task. Therefore, we register JavaCompile as the generating task.
                artifacts.appendArtifact(
                        InternalArtifactType.DATA_BINDING_ARTIFACT,
                        ImmutableList.of(scope.getBundleArtifactFolderForDataBinding()),
                        javacTask)
            }

            javacTask.processorListFile = artifacts.getFinalArtifactFiles(ANNOTATION_PROCESSOR_LIST)
//            javacTask.processorListFile?.files?.forEach {
//                println("processorListFile: ${it.absolutePath}")
//            }
            javacTask.variantName = scope.getFullName()

        }

        private fun isIncremental(
                project: Project,
                variantScope: PluginVariantScope,
                compileOptions: CompileOptions,
                processorConfiguration: Configuration?,
                log: ILogger): Boolean {
            var incremental = true
            if (compileOptions.incremental != null) {
                incremental = compileOptions.incremental!!
                log.verbose("Incremental flag set to %1\$b in DSL", incremental)
            } else {
                val hasAnnotationProcessor = processorConfiguration != null && !processorConfiguration.allDependencies.isEmpty()
                if (variantScope.getGlobalScope().extension.dataBinding.isEnabled
                        || hasAnnotationProcessor
                        || project.plugins.hasPlugin("me.tatarka.retrolambda")) {
                    incremental = false
                    log.verbose("Incremental Java compilation disabled in variant %1\$s " + "as you are using an incompatible plugin",
                            variantScope.getVariantConfiguration().fullName)
                }
            }
            return incremental
        }
    }
}