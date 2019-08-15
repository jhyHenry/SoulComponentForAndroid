package cn.soul.android.plugin.component

import cn.soul.android.plugin.component.tasks.*
import cn.soul.android.plugin.component.tasks.transform.FilterClassTransform
import com.android.SdkConstants.*
import com.android.build.api.transform.QualifiedContent
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.InternalScope
import com.android.build.gradle.internal.TaskFactory
import com.android.build.gradle.internal.TaskFactoryImpl
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.pipeline.ExtendedContentType
import com.android.build.gradle.internal.pipeline.OriginalStream
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.*
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.*
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.transforms.LibraryAarJarsTransform
import com.android.build.gradle.internal.transforms.LibraryIntermediateJarsTransform
import com.android.build.gradle.internal.transforms.LibraryJniLibsTransform
import com.google.common.base.MoreObjects
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import java.io.File
import java.util.*

/**
 * @author panxinghai
 *
 * date : 2019-07-11 14:22
 */
class TaskManager(private val project: Project) {
    private var taskFactory: TaskFactory = PluginTaskFactory(TaskFactoryImpl(project.tasks))
    private val componentTaskContainer: MutableSet<Task> = mutableSetOf()

    fun isComponentTask(task: Task) = componentTaskContainer.contains(task)

    fun createAnchorTasks(scope: PluginVariantScope) {
        scope.getTaskContainer().resourceGenTask =
                taskFactory.create(scope.getTaskName("generate", "Resources"))
        scope.getTaskContainer().assetGenTask =
                taskFactory.create(scope.getTaskName("generate", "Assets"))
        componentTaskContainer.add(scope.getTaskContainer().resourceGenTask!!)
        componentTaskContainer.add(scope.getTaskContainer().assetGenTask!!)
    }

    fun createCheckManifestTask(scope: PluginVariantScope) {
        val task = taskFactory.create(CheckManifest.ConfigAction(scope, false))
        scope.getTaskContainer().pluginCheckManifestTask = task
        task.dependsOn(scope.getTaskContainer().preBuildTask)
        componentTaskContainer.add(task)
    }

    fun createMergeLibManifestsTask(scope: PluginVariantScope) {
        val processManifest = taskFactory.create(ProcessManifest.ConfigAction(scope))

        processManifest.dependsOn(scope.getTaskContainer().checkManifestTask!!)

        scope.getTaskContainer().pluginProcessManifest = processManifest

        componentTaskContainer.add(processManifest)
    }

    fun createAidlTask(scope: PluginVariantScope) {
        val task = taskFactory.create(AidlCompile.ConfigAction(scope))
        scope.getTaskContainer().pluginAidlCompile = task
        task.dependsOn(scope.getTaskContainer().preBuildTask)
        componentTaskContainer.add(task)
    }

    fun createMergeResourcesTask(scope: PluginVariantScope) {
        val flags: ImmutableSet<MergeResources.Flag> = if (java.lang.Boolean.TRUE == scope.globalScope.extension.aaptOptions.namespaced) {
            Sets.immutableEnumSet(
                    MergeResources.Flag.REMOVE_RESOURCE_NAMESPACES,
                    MergeResources.Flag.PROCESS_VECTOR_DRAWABLES)
        } else {
            Sets.immutableEnumSet(MergeResources.Flag.PROCESS_VECTOR_DRAWABLES)
        }

        // Create a merge task to only merge the resources from this library and not
        // the dependencies. This is what gets packaged in the aar.
        val packageResourcesTask = basicCreateMergeResourcesTask(
                scope,
                TaskManager.MergeType.PACKAGE,
                scope.getIntermediateDir(InternalArtifactType.PACKAGED_RES),
                includeDependencies = false,
                processResources = false,
                alsoOutputNotCompiledResources = false,
                flags = flags)

        packageResourcesTask.setPublicFile(
                scope.getArtifacts()
                        .appendArtifact(
                                InternalArtifactType.PUBLIC_RES,
                                packageResourcesTask,
                                FN_PUBLIC_TXT))
        scope.getTaskContainer().pluginMergeResourcesTask = packageResourcesTask
//        createMergeResourcesTask(scope, processResources = false, flags = ImmutableSet.of())
        componentTaskContainer.add(packageResourcesTask)
    }

    fun createRefineManifestTask(scope: PluginVariantScope) {
        val processManifestFile = scope.getTaskContainer().pluginProcessManifest?.processorManifestOutputFile
        val task = taskFactory.create(RefineManifest.ConfigAction(scope, processManifestFile!!))
        componentTaskContainer.add(task)
    }

    fun createPrefixResourcesTask(scope: PluginVariantScope) {
        val file = scope.getIntermediateDir(InternalArtifactType.PACKAGED_RES)
        var prefix = scope.getComponentExtension().resourcePrefix
        if (prefix == null) {
            prefix = "${project.name}_"
        }
        val task = taskFactory.create(PrefixResources.ConfigAction(scope, file, prefix))
        task.dependsOn(scope.getTaskContainer().pluginMergeResourcesTask)
        scope.getTaskContainer().pluginPrefixResources = task
    }

    fun createBundleTask(scope: PluginVariantScope) {
        val task = taskFactory.create(BundleAar.ConfigAction(scope.globalScope.extension, scope))
        scope.getTaskContainer().pluginBundleAarTask = task
        task.dependsOn(scope.getTaskContainer().pluginPrefixResources!!)
        task.dependsOn(scope.getTaskContainer().pluginProcessManifest!!)
        componentTaskContainer.add(task)
    }

    fun addJavacClassesStream(javaOutputs: FileCollection, scope: PluginVariantScope) {
        val artifacts = scope.getArtifacts()

        Preconditions.checkNotNull(javaOutputs)
        // create separate streams for the output of JAVAC and for the pre/post javac
        // bytecode hooks
        scope.getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "javac-output")
                                // Need both classes and resources because some annotation
                                // processors generate resources
                                .addContentTypes(
                                        QualifiedContent.DefaultContentType.CLASSES, QualifiedContent.DefaultContentType.RESOURCES)
                                .addScope(QualifiedContent.Scope.PROJECT)
                                .setFileCollection(javaOutputs)
                                .build())

        scope.getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "pre-javac-generated-bytecode")
                                .addContentTypes(
                                        QualifiedContent.DefaultContentType.CLASSES, QualifiedContent.DefaultContentType.RESOURCES)
                                .addScope(QualifiedContent.Scope.PROJECT)
                                .setFileCollection(
                                        scope.getVariantData().allPreJavacGeneratedBytecode)
                                .build())

        scope.getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "post-javac-generated-bytecode")
                                .addContentTypes(
                                        QualifiedContent.DefaultContentType.CLASSES, QualifiedContent.DefaultContentType.RESOURCES)
                                .addScope(QualifiedContent.Scope.PROJECT)
                                .setFileCollection(
                                        scope.getVariantData().allPostJavacGeneratedBytecode)
                                .build())

        if (artifacts.hasArtifact(InternalArtifactType.RUNTIME_R_CLASS_CLASSES)) {
            scope.getTransformManager()
                    .addStream(
                            OriginalStream.builder(project, "final-r-classes")
                                    .addContentTypes(
                                            QualifiedContent.DefaultContentType.CLASSES,
                                            QualifiedContent.DefaultContentType.RESOURCES)
                                    .addScope(QualifiedContent.Scope.PROJECT)
                                    .setFileCollection(
                                            artifacts.getFinalArtifactFiles(
                                                    InternalArtifactType.RUNTIME_R_CLASS_CLASSES)
                                                    .get())
                                    .build())
        }
    }

    fun transform(scope: PluginVariantScope) {
        val transformManager = scope.getTransformManager()
        createFilterClassTransform(scope, transformManager)

        val jarOutputFolder = scope.getIntermediateJarOutputFolder()
        val mainClassJar = File(jarOutputFolder, FN_CLASSES_JAR)
        val mainResJar = File(jarOutputFolder, FN_INTERMEDIATE_RES_JAR)
        createIntermediateJarsTransform(mainClassJar, mainResJar, scope, transformManager)

        val jniLibsFolder = File(jarOutputFolder, FD_JNI)
        createIntermediateJniLibsTransform(jniLibsFolder, transformManager, scope)

        val classesJar = scope.getAarClassesJar()
        val libsDir = scope.getAarLibsDirectory()
        createAarJarsTransform(classesJar, libsDir, scope, transformManager)

        createSyncJniLibsTransform(scope, transformManager)
    }

    private fun createFilterClassTransform(scope: PluginVariantScope, transformManager: TransformManager) {
        transformManager.addTransform(taskFactory,
                scope,
                FilterClassTransform())
                .ifPresent {
                    componentTaskContainer.add(it)
                }
    }

    fun createUploadTask(scope: PluginVariantScope) {
        val versionName = project.extensions.getByType(AppExtension::class.java).defaultConfig.versionName
        val task = taskFactory.create(UploadComponent.ConfigAction(project.name, versionName, scope))
        scope.getTaskContainer().pluginUploadTask = task
        task.dependsOn(scope.getTaskContainer().pluginBundleAarTask)

        componentTaskContainer.add(task)
    }

    private fun createIntermediateJniLibsTransform(jniLibsFolder: File, transformManager: TransformManager, scope: PluginVariantScope) {
        val intermediateJniTransform = LibraryJniLibsTransform(
                "intermediateJniLibs",
                jniLibsFolder,
                TransformManager.PROJECT_ONLY)
        transformManager.addTransform(taskFactory,
                scope,
                intermediateJniTransform)
                .ifPresent {
                    PluginArtifactsHolder.appendArtifact(
                            InternalArtifactType.LIBRARY_JNI,
                            jniLibsFolder)
                    componentTaskContainer.add(it)
                }
    }

    private fun createIntermediateJarsTransform(mainClassJar: File, mainResJar: File, scope: PluginVariantScope, transformManager: TransformManager) {
        val intermediateTransform = LibraryIntermediateJarsTransform(mainClassJar,
                mainResJar,
                scope.getVariantConfiguration()::getPackageFromManifest,
                true)
        //add class file transform to jar file Task
        transformManager.addTransform(
                taskFactory,
                scope,
                intermediateTransform)
                .ifPresent {
                    PluginArtifactsHolder.appendArtifact(
                            InternalArtifactType.LIBRARY_CLASSES,
                            mainClassJar)
                    PluginArtifactsHolder.appendArtifact(
                            InternalArtifactType.LIBRARY_JAVA_RES,
                            mainResJar)
                    componentTaskContainer.add(it)
                }

        transformManager.addStream(
                OriginalStream.builder(project, "mergedJniFolder")
                        .addContentType(ExtendedContentType.NATIVE_LIBS)
                        .addScope(QualifiedContent.Scope.PROJECT)
                        .setFileCollection(project.files(scope.getMergeNativeLibsOutputDir()))
//                        .setDependency(mergeJniLibFoldersTask.getName())
                        .build())

        // create a stream that contains the content of the local NDK build
        transformManager.addStream(
                OriginalStream.builder(project, "local-ndk-build")
                        .addContentType(ExtendedContentType.NATIVE_LIBS)
                        .addScope(QualifiedContent.Scope.PROJECT)
                        .setFileCollection(project.files(scope.getNdkSoFolder()))
//                        .setDependency(getNdkBuildable(variantScope.getVariantData()))
                        .build())

        // create a stream that contains the content of the local external native build
    }

    private fun createAarJarsTransform(classesJar: File, libsDir: File, scope: PluginVariantScope, transformManager: TransformManager) {
        val artifacts = scope.getArtifacts()
        val transform = LibraryAarJarsTransform(classesJar,
                libsDir,
                if (scope.getArtifacts().hasArtifact(InternalArtifactType.ANNOTATIONS_TYPEDEF_FILE))
                    scope.getArtifacts().getFinalArtifactFiles(
                            InternalArtifactType.ANNOTATIONS_TYPEDEF_FILE)
                else
                    null,
                scope.getVariantConfiguration()::getPackageFromManifest,
                true)
        transformManager.addTransform(taskFactory, scope, transform)
                .ifPresent {
                    artifacts.appendArtifact(ComponentArtifactType.COMPONENT_AAR_MAIN_JAR,
                            ImmutableList.of(classesJar),
                            it)
                    artifacts.appendArtifact(ComponentArtifactType.COMPONENT_AAR_LIBS_DIR,
                            ImmutableList.of(libsDir),
                            it)
                    componentTaskContainer.add(it)
                }
    }

    private fun createMergeJavaResTransform(scope: PluginVariantScope, transformManager: TransformManager) {

        // Compute the scopes that need to be merged.
        val mergeScopes = getResMergingScopes(scope)

        // Create the merge transform.
//        val mergeTransform = MergeJavaResourcesTransform(
//                scope.globalScope.extension.packagingOptions,
//                mergeScopes,
//                QualifiedContent.DefaultContentType.RESOURCES,
//                "mergeJavaRes",
//                variantScope)
//        val transformTask = transformManager.addTransform(taskFactory, scope, mergeTransform)
////        variantScope.getTaskContainer().mergeJavaResourcesTask = transformTask.orElse(null)
//
//        val mergeJavaResOutput = FileUtils.join(
//                globalScope.getIntermediatesDir(),
//                "transforms",
//                "mergeJavaRes",
//                variantScope.getVariantConfiguration().getDirName(),
//                "0.jar")
//
//        if (transformTask.isPresent) {
//            scope
//                    .getArtifacts()
//                    .appendArtifact(
//                            InternalArtifactType.FEATURE_AND_RUNTIME_DEPS_JAVA_RES,
//                            ImmutableList.of<File>(mergeJavaResOutput),
//                            transformTask.get())
//        }
    }

    private fun getResMergingScopes(variantScope: PluginVariantScope): MutableSet<in QualifiedContent.ScopeType>? {
//        return if (variantScope. != null) {
//            TransformManager.SCOPE_FULL_PROJECT
//        } else TransformManager.PROJECT_ONLY
        return TransformManager.PROJECT_ONLY
    }

    private fun createMergeResourcesTask(
            scope: PluginVariantScope,
            processResources: Boolean,
            flags: ImmutableSet<MergeResources.Flag>): MergeResources {

        val alsoOutputNotCompiledResources = (scope.getVariantData().type.isApk
                && !scope.getVariantData().type.isForTesting
                && scope.useResourceShrinker())

        return basicCreateMergeResourcesTask(
                scope,
                TaskManager.MergeType.MERGE,
                null /*outputLocation*/,
                true /*includeDependencies*/,
                processResources,
                alsoOutputNotCompiledResources,
                flags)
    }

    private fun basicCreateMergeResourcesTask(
            scope: PluginVariantScope,
            mergeType: TaskManager.MergeType,
            outputLocation: File?,
            includeDependencies: Boolean,
            processResources: Boolean,
            alsoOutputNotCompiledResources: Boolean,
            flags: ImmutableSet<MergeResources.Flag>): MergeResources {

        val mergedOutputDir = MoreObjects
                .firstNonNull(outputLocation, scope.getDefaultMergeResourcesOutputDir())

        val taskNamePrefix = mergeType.name.toLowerCase(Locale.ENGLISH)

        val mergedNotCompiledDir = if (alsoOutputNotCompiledResources)
            File(scope.getIntermediatesDir()
                    , "/merged-not-compiled-resources/${scope.getVariantConfiguration().dirName}")
        else
            null

        val mergeResourcesTask = taskFactory.create(
                MergeResources.ConfigAction(
                        scope,
                        mergeType,
                        taskNamePrefix,
                        mergedOutputDir,
                        mergedNotCompiledDir,
                        includeDependencies,
                        processResources,
                        flags))

//        scope.getArtifacts().appendArtifact(mergeType.outputType,
//                ImmutableList.of(mergedOutputDir), mergeResourcesTask)

        if (alsoOutputNotCompiledResources) {
            scope.getArtifacts().appendArtifact(
                    InternalArtifactType.MERGED_NOT_COMPILED_RES,
                    ImmutableList.of(mergedNotCompiledDir!!),
                    mergeResourcesTask)
        }

        mergeResourcesTask.dependsOn(scope.getTaskContainer().resourceGenTask!!)

//        if (scope.getGlobalScope().extension.testOptions.unitTests.isIncludeAndroidResources) {
//            scope.getTaskContainer().compileTask.dependsOn(mergeResourcesTask)
//        }

        return mergeResourcesTask
    }

    fun createDependencyStreams(variantScope: PluginVariantScope, transformManager: TransformManager) {
        // Since it's going to chance the configurations, we need to do it before
        // we start doing queries to fill the streams.

        transformManager.addStream(
                OriginalStream.builder(project, "ext-libs-classes")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(
                                        RUNTIME_CLASSPATH,
                                        EXTERNAL,
                                        CLASSES))
                        .build())

        transformManager.addStream(
                OriginalStream.builder(project, "ext-libs-res-plus-native")
                        .addContentTypes(
                                QualifiedContent.DefaultContentType.RESOURCES,
                                ExtendedContentType.NATIVE_LIBS)
                        .addScope(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(
                                        RUNTIME_CLASSPATH,
                                        EXTERNAL,
                                        JAVA_RES))
                        .build())

        // and the android AAR also have a specific jni folder
        transformManager.addStream(
                OriginalStream.builder(project, "ext-libs-native")
                        .addContentTypes(TransformManager.CONTENT_NATIVE_LIBS)
                        .addScope(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(
                                        RUNTIME_CLASSPATH,
                                        EXTERNAL,
                                        JNI))
                        .build())

        // data binding related artifacts for external libs
        if (variantScope.globalScope.extension.dataBinding.isEnabled) {
            transformManager.addStream(
                    OriginalStream.builder(project, "sub-project-data-binding-base-classes")
                            .addContentTypes(TransformManager.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT)
                            .addScope(QualifiedContent.Scope.SUB_PROJECTS)
                            .setArtifactCollection(
                                    variantScope.getArtifactCollection(
                                            COMPILE_CLASSPATH,
                                            MODULE,
                                            DATA_BINDING_BASE_CLASS_LOG_ARTIFACT))
                            .build())
            transformManager.addStream(
                    OriginalStream.builder(project, "ext-libs-data-binding-base-classes")
                            .addContentTypes(TransformManager.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT)
                            .addScope(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                            .setArtifactCollection(
                                    variantScope.getArtifactCollection(
                                            COMPILE_CLASSPATH,
                                            EXTERNAL,
                                            DATA_BINDING_BASE_CLASS_LOG_ARTIFACT))
                            .build())
        }

        // for the sub modules, new intermediary classes artifact has its own stream
        transformManager.addStream(
                OriginalStream.builder(project, "sub-projects-classes")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(QualifiedContent.Scope.SUB_PROJECTS)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(
                                        RUNTIME_CLASSPATH,
                                        MODULE,
                                        CLASSES))
                        .build())

        // same for the resources which can be java-res or jni
        transformManager.addStream(
                OriginalStream.builder(project, "sub-projects-res-plus-native")
                        .addContentTypes(
                                QualifiedContent.DefaultContentType.RESOURCES, ExtendedContentType.NATIVE_LIBS)
                        .addScope(QualifiedContent.Scope.SUB_PROJECTS)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(
                                        RUNTIME_CLASSPATH,
                                        MODULE,
                                        JAVA_RES))
                        .build())

        // and the android library sub-modules also have a specific jni folder
        transformManager.addStream(
                OriginalStream.builder(project, "sub-projects-native")
                        .addContentTypes(TransformManager.CONTENT_NATIVE_LIBS)
                        .addScope(QualifiedContent.Scope.SUB_PROJECTS)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(
                                        RUNTIME_CLASSPATH,
                                        MODULE,
                                        JNI))
                        .build())

        // if variantScope.consumesFeatureJars(), add streams of classes and java resources from
        // features or dynamic-features.
        // The main dex list calculation for the bundle also needs the feature classes for reference
        // only
        if (variantScope.consumesFeatureJars() || variantScope.getNeedsMainDexListForBundle()) {
            transformManager.addStream(
                    OriginalStream.builder(project, "metadata-classes")
                            .addContentTypes(TransformManager.CONTENT_CLASS)
                            .addScope(InternalScope.FEATURES)
                            .setArtifactCollection(
                                    variantScope.getArtifactCollection(
                                            METADATA_VALUES, MODULE, METADATA_CLASSES))
                            .build())
        }
        if (variantScope.consumesFeatureJars()) {
            transformManager.addStream(
                    OriginalStream.builder(project, "metadata-java-res")
                            .addContentTypes(TransformManager.CONTENT_RESOURCES)
                            .addScope(InternalScope.FEATURES)
                            .setArtifactCollection(
                                    variantScope.getArtifactCollection(
                                            METADATA_VALUES, MODULE, METADATA_JAVA_RES))
                            .build())
        }

        // provided only scopes.
        transformManager.addStream(
                OriginalStream.builder(project, "provided-classes")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(QualifiedContent.Scope.PROVIDED_ONLY)
                        .setFileCollection(variantScope.getProvidedOnlyClasspath())
                        .build())
        transformManager.addStream(
                OriginalStream.builder(project, "local-deps-classes")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(InternalScope.LOCAL_DEPS)
                        .setFileCollection(variantScope.getLocalPackagedJars())
                        .build())

        transformManager.addStream(
                OriginalStream.builder(project, "local-deps-native")
                        .addContentTypes(
                                QualifiedContent.DefaultContentType.RESOURCES,
                                ExtendedContentType.NATIVE_LIBS)
                        .addScope(InternalScope.LOCAL_DEPS)
                        .setFileCollection(variantScope.getLocalPackagedJars())
                        .build())
    }

    private fun createSyncJniLibsTransform(variantScope: PluginVariantScope, transformManager: TransformManager) {
        val jniLibsFolder = variantScope.getIntermediateDir(InternalArtifactType.LIBRARY_AND_LOCAL_JARS_JNI)
        val jniTransform = LibraryJniLibsTransform(
                "syncJniLibs",
                jniLibsFolder,
                TransformManager.SCOPE_FULL_LIBRARY_WITH_LOCAL_JARS)
        val jniPackagingTask = transformManager.addTransform(taskFactory, variantScope, jniTransform)
        jniPackagingTask.ifPresent { t ->
            variantScope
                    .getArtifacts()
                    .appendArtifact(
                            InternalArtifactType.LIBRARY_AND_LOCAL_JARS_JNI,
                            ImmutableList.of(jniLibsFolder),
                            t)
            componentTaskContainer.add(t)
        }
    }
}