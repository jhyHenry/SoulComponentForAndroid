package cn.soul.android.plugin.component

import cn.soul.android.plugin.component.tasks.*
import cn.soul.android.plugin.component.tasks.transform.FilterClassTransform
import cn.soul.android.plugin.component.tasks.transform.MergeJavaResourcesTransform
import cn.soul.android.plugin.component.utils.Descriptor
import com.android.SdkConstants.*
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
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
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.transforms.LibraryAarJarsTransform
import com.android.build.gradle.internal.transforms.LibraryIntermediateJarsTransform
import com.android.build.gradle.internal.transforms.LibraryJniLibsTransform
import com.android.build.gradle.internal.variant.VariantHelper
import com.android.build.gradle.tasks.MergeSourceSetFolders
import com.android.utils.FileUtils
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
    private var taskFactory: TaskFactory = PluginTaskFactory(TaskFactoryImpl(project.tasks), this)
    val componentTaskContainer: MutableSet<Task> = mutableSetOf()

    fun isComponentTask(task: Task) = componentTaskContainer.contains(task)

    fun isComponentTask(taskName: String): Boolean {
        componentTaskContainer.forEach {
            if (it.name == taskName) {
                return true
            }
        }
        return false
    }

    fun createAnchorTasks(scope: PluginVariantScope) {
        scope.getTaskContainer().resourceGenTask =
                taskFactory.create(scope.getTaskName("generate", "Resources"))
        scope.getTaskContainer().assetGenTask =
                taskFactory.create(scope.getTaskName("generate", "Assets"))
        scope.getTaskContainer().sourceGenTask =
                taskFactory.create(scope.getTaskName("generate", "Source"))
//        scope.getTaskContainer().sourceGenTask?.dependsOn(scope.getRealScope().taskContainer.externalNativeBuildTask)
    }

    fun createCheckManifestTask(scope: PluginVariantScope) {
        val task = taskFactory.create(CheckManifest.ConfigAction(scope, false))
        scope.getTaskContainer().pluginCheckManifestTask = task
        task.dependsOn(scope.getTaskContainer().preBuildTask)
    }

    fun createMergeLibManifestsTask(scope: PluginVariantScope) {
        val processManifest = taskFactory.create(ProcessManifest.ConfigAction(scope))

        processManifest.dependsOn(scope.getTaskContainer().checkManifestTask!!)

        scope.getTaskContainer().pluginProcessManifest = processManifest
    }

    fun createAidlTask(scope: PluginVariantScope) {
        val task = taskFactory.create(AidlCompile.ConfigAction(scope))
        scope.getTaskContainer().pluginAidlCompile = task
        task.dependsOn(scope.getTaskContainer().preBuildTask)
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
    }

    fun createGenerateSymbolTask(scope: PluginVariantScope) {
        val dir = File(scope.getIntermediatesDir(),
                "symbols/" + scope
                        .getVariantData()
                        .variantConfiguration
                        .dirName)
        val symbol = File(dir, FN_RESOURCE_TEXT)
        val symbolTableWithPackageName = FileUtils.join(
                scope.getIntermediatesDir(),
                FD_RES,
                "symbol-table-with-package",
                scope.getVariantConfiguration().dirName,
                "package-aware-r.txt")
        val task = taskFactory.create(GenerateSymbol.ConfigAction(scope,
                symbol,
                symbolTableWithPackageName))
        scope.getArtifacts().appendArtifact(
                ComponentArtifactType.COMPONENT_R_TXT,
                ImmutableList.of(symbol),
                task)
        scope.getTaskContainer().pluginGenerateSymbol = task
    }

    fun createRefineManifestTask(scope: PluginVariantScope) {
        val processManifestFile = scope.getTaskContainer().pluginProcessManifest?.processorManifestOutputFile
        val task = taskFactory.create(RefineManifest.ConfigAction(scope, processManifestFile!!))
//        componentTaskContainer.add(task)
    }

    fun createPrefixResourcesTask(scope: PluginVariantScope) {
        val file = scope.getIntermediateDir(InternalArtifactType.PACKAGED_RES)
        var prefix = scope.getComponentExtension().resourcePrefix
        if (prefix == null) {
            prefix = "${project.name}_"
        }
        val task = taskFactory.create(PrefixResources.ConfigAction(scope, file, prefix))
        scope.getArtifacts().appendArtifact(InternalArtifactType.PACKAGED_RES,
                ImmutableList.of(file),
                task)
        task.dependsOn(scope.getTaskContainer().pluginMergeResourcesTask)
        scope.getTaskContainer().pluginPrefixResources = task
    }

    fun createLibraryAssetsTask(scope: PluginVariantScope) {
        val mergeAssetsTask = taskFactory.create(MergeSourceSetFolders.LibraryAssetConfigAction(scope.getRealScope()))
        scope.getTaskContainer().pluginMergeAssetsTask = mergeAssetsTask
    }

    fun crateGenInterfaceArtifactTask(scope: PluginVariantScope) {
        val file = scope.getArtifacts().getFinalArtifactFiles(InternalArtifactType.JAVAC).single()
        val task = taskFactory.create(GenerateInterfaceArtifact.ConfigAction(scope, file))
        task.dependsOn(scope.getTaskContainer().javacTask)
        scope.getTaskContainer().pluginGenInterface = task
        if (scope.getVariantConfiguration().buildType.name != "release") {
            return
        }
        val uploadTaskPrefix = "uploadComponent"
        if (project.gradle.startParameter.taskNames.size == 0) {
            return
        }
        val startTaskName = Descriptor.getTaskNameWithoutModule(project.gradle.startParameter.taskNames[0])
        if (startTaskName.startsWith(uploadTaskPrefix)) {
            val flavor = startTaskName.substring(uploadTaskPrefix.length)
            if (flavor.toLowerCase(Locale.getDefault()) == scope.getRealScope().variantConfiguration.flavorName) {
                project.artifacts.add("archives", File(task.destDir, "interface.jar"))
            }
        }
    }

    fun createBundleTask(scope: PluginVariantScope) {
        val task = taskFactory.create(BundleAar.ConfigAction(scope.globalScope.extension, scope))
        scope.getTaskContainer().pluginBundleAarTask = task
        task.dependsOn(scope.getTaskContainer().pluginPrefixResources!!)
        task.dependsOn(scope.getTaskContainer().pluginProcessManifest!!)
        task.dependsOn(scope.getTaskContainer().pluginGenerateSymbol!!)
//        componentTaskContainer.add(task)

        scope.getArtifacts().appendArtifact(ComponentArtifactType.COMPONENT_AAR,
                ImmutableList.of(File(scope.getAarLocation(), "component.aar")),
                task)
        val tTask = project.tasks.findByName("componentTransformClassesAndResourcesWithSyncLibJarsForProRelease")
        tTask?.taskDependencies?.getDependencies(tTask)?.forEach {
            println(it)
        }

        if (scope.getVariantConfiguration().buildType.name != "release") {
            return
        }
        val uploadTaskPrefix = "uploadComponent"
        if (project.gradle.startParameter.taskNames.size == 0) {
            return
        }
        val startTaskName = Descriptor.getTaskNameWithoutModule(project.gradle.startParameter.taskNames[0])
        if (startTaskName.startsWith(uploadTaskPrefix)) {
            val flavor = startTaskName.substring(uploadTaskPrefix.length)
            if (flavor.toLowerCase(Locale.getDefault()) == scope.getRealScope().variantConfiguration.flavorName) {
                VariantHelper.setupArchivesConfig(project, scope.getRealScope().variantDependencies.runtimeClasspath)
                project.artifacts.add("archives", task)
            }
        }
    }

    fun addJavacClassesStream(javaOutputs: FileCollection, scope: PluginVariantScope) {
        val artifacts = scope.getArtifacts()
        Preconditions.checkNotNull(javaOutputs)
        // create separate streams for the output of JAVAC and for the pre/post javac
        // bytecode hooks.
        // in here, use applicationTransform output, so just need javac-output
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
    }

    fun transform(scope: PluginVariantScope) {
        val transformManager = scope.getTransformManager()

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

    fun addPluginTransform(scope: PluginVariantScope, transform: Transform) {
        scope.getTransformManager().addTransform(taskFactory, scope, transform)
    }

    fun createFilterClassTransform(scope: PluginVariantScope, transformManager: TransformManager) {
        transformManager.addTransform(taskFactory,
                scope,
                FilterClassTransform())
    }

    fun createReplaceManifestTask(scope: PluginVariantScope) {
        val manifestFile = scope.getRealScope().taskContainer.packageAndroidTask?.manifests?.single()
                ?: return
        val task = taskFactory.create(ReplaceManifest.ConfigAction(scope, File(manifestFile, "AndroidManifest.xml")))
        scope.getRealScope().taskContainer.processAndroidResTask?.dependsOn(task)
        task.dependsOn(scope.getRealScope().taskContainer.processManifestTask)
    }

    fun createUploadTask(scope: PluginVariantScope) {
        if (scope.getVariantConfiguration().buildType.name != "release") {
            return
        }
        val task = taskFactory.create(UploadComponent.ConfigAction(scope, project))
        scope.getTaskContainer().pluginUploadTask = task
        task.dependsOn(scope.getTaskContainer().pluginGenInterface!!)
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
                }
    }

    private fun createIntermediateJarsTransform(mainClassJar: File, mainResJar: File, scope: PluginVariantScope, transformManager: TransformManager) {
        val intermediateTransform = LibraryIntermediateJarsTransform(mainClassJar,
                mainResJar,
                scope.getVariantConfiguration()::getPackageFromManifest,
                true)

        val mergeJniLibFoldersTask = project.tasks.findByName(scope.getRealScope().getTaskName("merge", "JniLibFolders"))
        val realTaskContainer = scope.getRealScope().taskContainer
        val externalNativeBuildTask = realTaskContainer.externalNativeBuildTask
        if (externalNativeBuildTask != null) {
            mergeJniLibFoldersTask?.dependsOn(realTaskContainer.externalNativeBuildTask)
            realTaskContainer.compileTask.dependsOn(mergeJniLibFoldersTask)
        }

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
                }

        transformManager.addStream(
                OriginalStream.builder(project, "mergedJniFolder")
                        .addContentType(ExtendedContentType.NATIVE_LIBS)
                        .addScope(QualifiedContent.Scope.PROJECT)
                        .setFileCollection(project.files(scope.getMergeNativeLibsOutputDir()))
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
        if (scope.getRealScope().taskContainer.externalNativeJsonGenerator != null) {
            transformManager.addStream(
                    OriginalStream.builder(project, "external-native-build")
                            .addContentType(ExtendedContentType.NATIVE_LIBS)
                            .addScope(QualifiedContent.Scope.PROJECT)
                            .setFileCollection(project.files(scope.getRealScope().taskContainer.externalNativeJsonGenerator?.objFolder))
                            .build())
        }
        // compute the scopes that need to be merged.
        val mergeScopes = getResMergingScopes(scope.getRealScope())
        // Create the merge transform
        val mergeTransform = MergeJavaResourcesTransform(
                scope.getRealScope().globalScope.extension.packagingOptions,
                mergeScopes, ExtendedContentType.NATIVE_LIBS, "mergeJniLibs", scope)
        transformManager
                .addTransform(taskFactory, scope, mergeTransform).ifPresent {
                    it.dependsOn(realTaskContainer.compileTask)
                }
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
                }
    }

//    private fun createMergeJavaResTransform(scope: PluginVariantScope, transformManager: TransformManager) {
//
//        // Compute the scopes that need to be merged.
//        val mergeScopes = getResMergingScopes(scope.getRealScope())
//
//        // Create the merge transform.
//        val mergeTransform = MergeJavaResourcesTransform(
//                scope.globalScope.extension.packagingOptions,
//                mergeScopes,
//                QualifiedContent.DefaultContentType.RESOURCES,
//                "mergeJavaRes",
//                scope)
//        val transformTask = transformManager.addTransform(taskFactory, scope, mergeTransform)
////        variantScope.getTaskContainer().mergeJavaResourcesTask = transformTask.orElse(null)
//
//        val mergeJavaResOutput = FileUtils.join(
//                scope.getIntermediatesDir(),
//                "transforms",
//                "mergeJavaRes",
//                scope.getVariantConfiguration().getDirName(),
//                "0.jar")
//
//        if (transformTask.isPresent) {
//            scope.getArtifacts()
//                    .appendArtifact(
//                            InternalArtifactType.FEATURE_AND_RUNTIME_DEPS_JAVA_RES,
//                            ImmutableList.of<File>(mergeJavaResOutput),
//                            transformTask.get())
//        }
//    }

    private fun getResMergingScopes(variantScope: VariantScope): MutableSet<in QualifiedContent.Scope> {
        return if (variantScope.testedVariantData != null) {
            TransformManager.SCOPE_FULL_PROJECT
        } else TransformManager.PROJECT_ONLY
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
        }
    }
}