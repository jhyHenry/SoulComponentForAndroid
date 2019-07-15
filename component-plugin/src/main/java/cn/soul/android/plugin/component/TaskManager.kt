package cn.soul.android.plugin.component

import cn.soul.android.plugin.component.tasks.*
import com.android.SdkConstants.FN_PUBLIC_TXT
import com.android.build.api.transform.QualifiedContent
import com.android.build.gradle.internal.TaskFactory
import com.android.build.gradle.internal.TaskFactoryImpl
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.pipeline.OriginalStream
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.google.common.base.MoreObjects
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File
import java.util.*

/**
 * @author panxinghai
 *
 * date : 2019-07-11 14:22
 */
class TaskManager(private val project: Project) {
    private var taskFactory: TaskFactory = PluginTaskFactory(TaskFactoryImpl(project.tasks))

    fun createAnchorTasks(scope: PluginVariantScope) {
        scope.getTaskContainer().resourceGenTask =
                taskFactory.create(scope.getTaskName("generate", "Resources"))
        scope.getTaskContainer().assetGenTask =
                taskFactory.create(scope.getTaskName("generate", "Assets"))

    }

    fun createCheckManifestTask(scope: PluginVariantScope) {
        val task = taskFactory.create(CheckManifest.ConfigAction(scope, false))
        scope.getTaskContainer().pluginCheckManifestTask = task
        task.dependsOn(scope.getTaskContainer().preBuildTask)
    }

    fun createAidlTask(scope: PluginVariantScope) {
        val task = taskFactory.create(AidlCompile.ConfigAction(scope))
        scope.getTaskContainer().pluginAidlCompile = task
        task.dependsOn(scope.getTaskContainer().preBuildTask)
    }

    fun createJavacTask(scope: PluginVariantScope): Task {
        val preCompileTask = taskFactory.create(JavaPreCompileTask.ConfigAction(scope))
        preCompileTask.dependsOn(scope.getTaskContainer().preBuildTask)

        val javacTask = taskFactory.create(AndroidJavaCompile.JavaCompileConfigAction(scope))
        scope.getTaskContainer().pluginJavacTask = javacTask
//        javacTask.dependsOn(preCompileTask)
        return javacTask
    }

    fun createMergeResourcesTask(scope: PluginVariantScope) {
        val flags: ImmutableSet<MergeResources.Flag>
        if (java.lang.Boolean.TRUE == scope.getGlobalScope().extension.aaptOptions.namespaced) {
            flags = Sets.immutableEnumSet(
                    MergeResources.Flag.REMOVE_RESOURCE_NAMESPACES,
                    MergeResources.Flag.PROCESS_VECTOR_DRAWABLES)
        } else {
            flags = Sets.immutableEnumSet(MergeResources.Flag.PROCESS_VECTOR_DRAWABLES)
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
    }

    fun createBundleTask(scope: PluginVariantScope) {
        val task = taskFactory.create(BundleAar.ConfigAction(scope.getGlobalScope().extension, scope))
        task.dependsOn(scope.getTaskContainer().pluginMergeResourcesTask)
    }

    fun addJavacClassesStream(scope: PluginVariantScope) {
        val artifacts = scope.getArtifacts()
        val javaOutputs = project.files(PluginArtifactsHolder.getArtifactFile(InternalArtifactType.JAVAC))

        Preconditions.checkNotNull(javaOutputs)
        // create separate streams for the output of JAVAC and for the pre/post javac
        // bytecode hooks
        scope.getVariantData().allPreJavacGeneratedBytecode.files.forEach {
            println("pre:${it.absolutePath}")
        }
        scope.getVariantData().allPostJavacGeneratedBytecode.files.forEach {
            println("post:${it.absolutePath}")
        }
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
}