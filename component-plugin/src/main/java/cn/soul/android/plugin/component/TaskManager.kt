package cn.soul.android.plugin.component

import cn.soul.android.plugin.component.exception.RGenerateException
import cn.soul.android.plugin.component.extesion.ComponentExtension
import cn.soul.android.plugin.component.resolve.PrefixHelper
import cn.soul.android.plugin.component.tasks.*
import cn.soul.android.plugin.component.utils.Descriptor
import cn.soul.android.plugin.component.utils.Log
import com.android.SdkConstants
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.TaskFactory
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl
import com.android.build.gradle.internal.transforms.BaseProguardAction
import com.android.build.gradle.internal.variant.VariantHelper
import com.android.build.gradle.tasks.ProcessLibraryManifest
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.gradle.api.Project
import org.gradle.api.file.FileSystemLocation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.util.*

/**
 * 创建task的管理类，每个task的用途参见其本身注释
 *
 * @author panxinghai
 *
 * date : 2019-07-11 14:22
 */
class TaskManager(private val project: Project, private val extension: ComponentExtension) {

    private var taskFactory: TaskFactory = PluginTaskFactory(TaskFactoryImpl(project.tasks))
    var pluginTaskContainer: PluginTaskContainer? = null

    fun createRefineManifestTask(scope: VariantScope) {
        val processTask = scope.taskContainer.processManifestTask?.get()
        if (processTask !is ProcessLibraryManifest) {
            return
        }
        val manifestOutput = processTask.manifestOutputFile.get().asFile ?: return
        val task = taskFactory.register(RefineManifest.ConfigAction(scope, manifestOutput))
        scope.taskContainer.bundleLibraryTask?.get()?.dependsOn(task)
    }

    fun createPrefixResourcesTask(scope: VariantScope) {
        val file = scope.getIntermediateDir(InternalArtifactType.PACKAGED_RES)
        var prefix = extension.resourcePrefix
        if (prefix == null) {
            prefix = "${project.name}_"
        }
        prefix.replace('-', '_')
        val task = taskFactory.register(PrefixResources.ConfigAction(scope, file, prefix))
        task.get().dependsOn(scope.taskContainer.mergeResourcesTask)
        pluginTaskContainer?.prefixResources = task.get()
        scope.taskContainer.bundleLibraryTask?.get()?.dependsOn(task)
//        //use ParseLibraryResourcesTask's output
//        val parseResourcesTaskName = scope.getTaskName("parse", "LibraryResources")
//        val parseResourcesTask = project.tasks.getByName(parseResourcesTaskName)
//        parseResourcesTask.dependsOn(task)
    }

    fun createGenerateSymbolTask(scope: VariantScope) {

        if (scope.variantConfiguration.buildType.name != "release" || project.gradle.startParameter.taskNames.size == 0) {
            return
        }

        val dir = File(scope.globalScope.intermediatesDir,
                "symbols/" + scope.variantData.variantConfiguration.dirName)
        val symbol = File(dir, SdkConstants.FN_RESOURCE_TEXT)
        val symbolTableWithPackageName = FileUtils.join(
                scope.globalScope.intermediatesDir,
                SdkConstants.FD_RES,
                "symbol-table-with-package",
                scope.variantConfiguration.dirName,
                "package-aware-r.txt")

        val externalDir = File(scope.globalScope.intermediatesDir, "Khala-PrefixRFile")
        // 存储合成R文件
        PrefixHelper.instance.symbolTableWithPackageName = symbolTableWithPackageName

        val task = taskFactory.register(GenerateSymbol.CreationAction(scope,
                symbol,
                symbolTableWithPackageName,
                externalDir))
        scope.artifacts.createBuildableArtifact(
                InternalArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME,
                BuildArtifactsHolder.OperationType.TRANSFORM,
                ImmutableList.of(symbol),
                task.name
        )
        task.get().dependsOn(pluginTaskContainer?.prefixResources)
        scope.taskContainer.bundleLibraryTask?.get()?.dependsOn(task)
        pluginTaskContainer?.generateSymbol = task.get()
//
//        val kotlinCompileTask = project.tasks.findByName("compile${scope.fullVariantName.capitalize()}Kotlin")
//        if (scope.variantConfiguration.buildType.name == "release") {
//            task.get().dependsOn.forEach {
//                println("RFile:$it")
//            }
//            println(kotlinCompileTask)
//            kotlinCompileTask!!.dependsOn.forEach {
//                println(it)
//            }
////            task.get().dependsOn(kotlinCompileTask)
//            project.afterEvaluate {
//                kotlinCompileTask!!.dependsOn.forEach {
//                    println(it)
//                }
//            }
//        }

    }

    fun createGenInterfaceArtifactTask(scope: VariantScope) {
        val javaOutput = scope.artifacts.getFinalProduct<FileSystemLocation>(InternalArtifactType.JAVAC).get().asFile
        val kotlinOutput = (project.tasks.findByName("compile${scope.fullVariantName.capitalize()}Kotlin") as? KotlinCompile)?.destinationDir
        val task = taskFactory.register(GenerateInterfaceArtifact.ConfigAction(scope, javaOutput, kotlinOutput))
        task.get().dependsOn(scope.taskContainer.bundleLibraryTask)
        pluginTaskContainer?.genInterface = task.get()
        if (scope.variantConfiguration.buildType.name != "release" || project.gradle.startParameter.taskNames.size == 0) {
            return
        }
        val flavor = getFlavor()
        // return ""
        if (flavor != null) {
            if (flavor.toLowerCase(Locale.getDefault()) == scope.variantConfiguration.flavorName) {
                project.artifacts.add("archives", File(task.get().destDir, "${project.name}.jar"))
            }
        }
    }

    fun createReplaceManifestTask(scope: VariantScope) {
        val manifestFile = scope.taskContainer.packageAndroidTask?.get()?.manifests?.get()?.asFile
                ?: return
        val task = taskFactory.register(ReplaceManifest.ConfigAction(scope, File(manifestFile, "AndroidManifest.xml")))
        scope.taskContainer.processAndroidResTask?.get()?.dependsOn(task.get())
        task.get().dependsOn(scope.taskContainer.processManifestTask)
    }

    /**
     * 上传组件
     */
    fun createUploadTask(scope: VariantScope) {
        if (scope.variantConfiguration.buildType.name != "release" || project.gradle.startParameter.taskNames.size == 0) {
            return
        }
        val flavor = getFlavor()
        if (flavor != null) {
            if (flavor.toLowerCase(Locale.getDefault()) == scope.variantConfiguration.flavorName) {
                VariantHelper.setupArchivesConfig(project, scope.variantDependencies.runtimeClasspath)
                project.artifacts.add("archives", scope.taskContainer.bundleLibraryTask!!)
            }
        }
        val task = taskFactory.register(UploadComponent.ConfigAction(scope, project))
        pluginTaskContainer?.uploadTask = task.get()
        task.get().dependsOn(scope.taskContainer.bundleLibraryTask)
        task.get().dependsOn(pluginTaskContainer?.genInterface!!)
    }

    /**
     * 依赖本地组件
     */
    fun createLocalTask(scope: VariantScope) {
        if (scope.variantConfiguration.buildType.name != "release" || project.gradle.startParameter.taskNames.size == 0) {
            return
        }
        val flavor = getFlavor()
        if (flavor != null) {
            if (flavor.toLowerCase(Locale.getDefault()) == scope.variantConfiguration.flavorName) {
                VariantHelper.setupArchivesConfig(project, scope.variantDependencies.runtimeClasspath)
                project.artifacts.add("archives", scope.taskContainer.bundleLibraryTask!!)
            }
        }
        val task = taskFactory.register(LocalComponent.ConfigAction(scope, project))
        pluginTaskContainer?.uploadTask = task.get()
        task.get().dependsOn(scope.taskContainer.bundleLibraryTask)
        task.get().dependsOn(pluginTaskContainer?.genInterface!!)

//        val taskAssemble: Task? = project.tasks.findByName("build")
//        Log.d("tasksbuild")
//        taskAssemble?.name?.let { Log.d(it) }
//        taskAssemble?.dependsOn(scope.taskContainer.bundleLibraryTask)
//        taskAssemble?.dependsOn(pluginTaskContainer?.genInterface!!)
    }

    // 生成本地依赖
    fun createLocalCompileTask(scope: VariantScope) {
        if (scope.variantConfiguration.buildType.name != "release" || project.gradle.startParameter.taskNames.size == 0) {
            return
        }
        val task = taskFactory.register(LocalCompile.ConfigAction(project))
        pluginTaskContainer?.uploadTask = task.get()
        task.get().dependsOn(scope.taskContainer.bundleLibraryTask)
        task.get().dependsOn(pluginTaskContainer?.genInterface!!)
    }

    // 混淆
    fun applyProguard(project: Project, scope: VariantScope) {
        if (scope.variantConfiguration.buildType.name != "release" || project.gradle.startParameter.taskNames.size == 0) {
            return
        }
        val proguardTask = project.tasks.findByName("transformClassesAndResourcesWithProguardFor${scope.fullVariantName.capitalize()}")
        if (proguardTask is TransformTask) {
            val transform = proguardTask.transform
            if (transform is BaseProguardAction) {
                transform.keep("public class cn.soul.android.khala.gen.** {*;}")
                transform.dontwarn("${scope.variantData.applicationId}.R$*")
            }
        }
    }

    private fun getFlavor(): String? {
        val uploadTaskPrefix = "uploadComponent"
        val localTaskPrefix = "localComponent"
        val localCompile = "localCompile"
        val commonTaskPrefix = "commonLocalComponent"

        val startTaskName = Descriptor.getTaskNameWithoutModule(project.gradle.startParameter.taskNames[0])
        if (startTaskName == commonTaskPrefix) {
            //todo:consider add flavor
            return ""
        }
        if (startTaskName.startsWith(localTaskPrefix)) {
            return startTaskName.substring(localTaskPrefix.length)
        }
        if (startTaskName.startsWith(uploadTaskPrefix)) {
            return startTaskName.substring(uploadTaskPrefix.length)
        }
        if (startTaskName.startsWith(localCompile)) {
            return startTaskName.substring(localCompile.length)
        }
        return null
    }
}