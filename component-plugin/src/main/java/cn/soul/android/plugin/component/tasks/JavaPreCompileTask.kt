package cn.soul.android.plugin.component.tasks

import cn.soul.android.plugin.component.PluginVariantScope
import com.android.annotations.NonNull
import com.android.annotations.VisibleForTesting
import com.android.build.gradle.api.AnnotationProcessorOptions
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.tasks.AndroidBuilderTask
import com.android.utils.FileUtils
import com.google.common.base.Joiner
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.google.gson.GsonBuilder
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.jar.JarFile
import java.util.stream.Collectors

/**
 * Created by nebula on 2019-07-14
 */

@CacheableTask
open class JavaPreCompileTask : AndroidBuilderTask() {
    @VisibleForTesting
    internal val DATA_BINDING_SPEC = "android.databinding.DataBinding"

    private val PROCESSOR_SERVICES = "META-INF/services/javax.annotation.processing.Processor"

    private var processorListFile: File? = null

    private var annotationProcessorConfigurationName: String? = null

    private var annotationProcessorConfiguration: ArtifactCollection? = null

    private var compileClasspaths: ArtifactCollection? = null

    private var annotationProcessorOptions: AnnotationProcessorOptions? = null

    private var isTestComponent: Boolean = false

    private var dataBindingEnabled: Boolean = false

    @VisibleForTesting
    internal fun init(
            @NonNull processorListFile: File,
            @NonNull annotationProcessorConfigurationName: String,
            @NonNull annotationProcessorConfiguration: ArtifactCollection,
            @NonNull compileClasspaths: ArtifactCollection,
            @NonNull annotationProcessorOptions: AnnotationProcessorOptions,
            isTestComponent: Boolean,
            dataBindingEnabled: Boolean) {
        this.processorListFile = processorListFile
        this.annotationProcessorConfigurationName = annotationProcessorConfigurationName
        this.annotationProcessorConfiguration = annotationProcessorConfiguration
        this.compileClasspaths = compileClasspaths
        this.annotationProcessorOptions = annotationProcessorOptions
        this.isTestComponent = isTestComponent
        this.dataBindingEnabled = dataBindingEnabled
    }

    @OutputFile
    fun getProcessorListFile(): File? {
        return processorListFile
    }

    @Classpath
    fun getAnnotationProcessorConfiguration(): FileCollection {
        return annotationProcessorConfiguration!!.artifactFiles
    }

    @Classpath
    fun getCompileClasspaths(): FileCollection {
        return compileClasspaths!!.artifactFiles
    }

    @TaskAction
    @Throws(IOException::class)
    fun preCompile() {
        val grandfathered = annotationProcessorOptions!!.includeCompileClasspath != null
        var compileProcessors: Collection<ResolvedArtifactResult>? = null
        if (!grandfathered) {
            compileProcessors = collectAnnotationProcessors(compileClasspaths!!)
            val annotationProcessors = annotationProcessorConfiguration!!.artifactFiles
            compileProcessors = compileProcessors
                    .stream()
                    .filter { artifact -> !annotationProcessors.contains(artifact.file) }
                    .collect(Collectors.toList())
            if (compileProcessors!!.isNotEmpty()) {
                val message = ("Annotation processors must be explicitly declared now.  The following "
                        + "dependencies on the compile classpath are found to contain "
                        + "annotation processor.  Please add them to the "
                        + annotationProcessorConfigurationName
                        + " configuration.\n  - "
                        + Joiner.on("\n  - ")
                        .join(convertArtifactsToNames(compileProcessors))
                        + "\nAlternatively, set "
                        + "android.defaultConfig.javaCompileOptions.annotationProcessorOptions.includeCompileClasspath = true "
                        + "to continue with previous behavior.  Note that this option "
                        + "is deprecated and will be removed in the future.\n"
                        + "See "
                        + "https://developer.android.com/r/tools/annotation-processor-error-message.html "
                        + "for more details.")
                if (isTestComponent) {
                    logger.warn(message)
                } else {
                    throw RuntimeException(message)
                }
            }
        }

        // Get all the annotation processors for metrics collection.
        val classNames = Sets.newHashSet<String>()

        // Add the annotation processors on classpath only when includeCompileClasspath is true.
        if (java.lang.Boolean.TRUE == annotationProcessorOptions!!.includeCompileClasspath) {
            if (compileProcessors == null) {
                compileProcessors = collectAnnotationProcessors(compileClasspaths!!)
            }
            classNames.addAll(convertArtifactsToNames(compileProcessors))
        }

        // Add all annotation processors on the annotation processor configuration.
        classNames.addAll(
                convertArtifactsToNames(
                        collectAnnotationProcessors(annotationProcessorConfiguration!!)))

        // Add the explicitly declared processors.
        // For metrics purposes, we don't care how they include the processor in their build.
        classNames.addAll(annotationProcessorOptions!!.classNames)

        // Add a generic reference to data binding, if present.
        if (dataBindingEnabled) {
            classNames.add(DATA_BINDING_SPEC)
        }

        FileUtils.deleteIfExists(processorListFile!!)
        val gson = GsonBuilder().create()
        FileWriter(processorListFile!!).use { writer -> gson.toJson(classNames, writer) }
    }

    /**
     * Returns a List of packages in the configuration believed to contain an annotation processor.
     *
     *
     * We assume a package has an annotation processor if it contains the
     * META-INF/services/javax.annotation.processing.Processor file.
     */
    private fun collectAnnotationProcessors(
            configuration: ArtifactCollection): List<ResolvedArtifactResult> {
        val processors = Lists.newArrayList<ResolvedArtifactResult>()
        for (artifact in configuration) {
            val file = artifact.file
            if (!file.exists()) {
                continue
            }
            if (file.isDirectory) {
                if (File(file, PROCESSOR_SERVICES).exists()) {
                    processors.add(artifact)
                }
            } else {
                try {
                    JarFile(file).use { jarFile ->
                        val entry = jarFile.getJarEntry(PROCESSOR_SERVICES)

                        if (entry != null) {
                            processors.add(artifact)
                        }
                    }
                } catch (iox: IOException) {
                    // Can happen when we encounter a folder instead of a jar; for instance, in
                    // sub-modules. We're just displaying a warning, so there's no need to stop the
                    // build here.
                }

            }
        }
        return processors
    }

    private fun convertArtifactsToNames(files: Collection<ResolvedArtifactResult>): List<String> {
        return files.stream()
                .map { artifact -> artifact.id.displayName }
                .collect(Collectors.toList())
    }

    class ConfigAction(private val scope: PluginVariantScope) : TaskConfigAction<JavaPreCompileTask> {

        @NonNull
        override fun getName(): String {
            return scope.getTaskName("javaPreCompile")
        }

        @NonNull
        override fun getType(): Class<JavaPreCompileTask> {
            return JavaPreCompileTask::class.java
        }

        override fun execute(@NonNull task: JavaPreCompileTask) {
            task.init(
                    scope.getInternalArtifactTypeOutputFile(InternalArtifactType.ANNOTATION_PROCESSOR_LIST,
                            task,
                            "annotationProcessors.json"),
                    if (scope.getVariantData().type.isTestComponent)
                        scope.getVariantData().type.prefix + "AnnotationProcessor"
                    else
                        "annotationProcessor",
                    scope.getArtifactCollection(AndroidArtifacts.ConsumedConfigType.ANNOTATION_PROCESSOR, AndroidArtifacts.ArtifactScope.ALL, AndroidArtifacts.ArtifactType.PROCESSED_JAR),
                    scope.getJavaClasspathArtifacts(AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH, AndroidArtifacts.ArtifactType.CLASSES, null),
                    scope.getVariantConfiguration()
                            .javaCompileOptions
                            .annotationProcessorOptions,
                    scope.getVariantData().type.isTestComponent,
                    false)
            task.variantName = scope.getVariantConfiguration().fullName
        }
    }
}