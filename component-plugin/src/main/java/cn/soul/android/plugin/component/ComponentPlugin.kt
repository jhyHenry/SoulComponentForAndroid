package cn.soul.android.plugin.component

import cn.soul.android.plugin.component.extesion.ComponentExtension
import cn.soul.android.plugin.component.manager.BuildType
import cn.soul.android.plugin.component.manager.StatusManager
import cn.soul.android.plugin.component.tasks.transform.CementAppTransform
import cn.soul.android.plugin.component.tasks.transform.CementLibTransform
import cn.soul.android.plugin.component.tasks.transform.PrefixRTransform
import cn.soul.android.plugin.component.utils.Descriptor
import cn.soul.android.plugin.component.utils.Log
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.LibraryPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.*

/**
 * @author panxinghai
 *
 * date : 2019-07-11 11:19
 */
class ComponentPlugin : Plugin<Project> {
    private lateinit var mPluginExtension: ComponentExtension
    private lateinit var project: Project
    private lateinit var taskManager: TaskManager

    private var mPrefixRTransform: PrefixRTransform? = null
    private var mCementTransform: CementAppTransform? = null

    override fun apply(p: Project) {
        project = p
        Log.p("apply component plugin. ")
        p.plugins.apply("maven")
        if (isRunForAar()) {
            Log.d("run for aar")
            p.plugins.apply("com.android.library")
            mPrefixRTransform = PrefixRTransform(project)
            val extension = project.extensions.findByType(BaseExtension::class.java)
            extension?.registerTransform(mPrefixRTransform)
            extension?.registerTransform(CementLibTransform(project))
        } else {
            Log.d("run for app")
            p.plugins.apply("com.android.application")
        }
        mPluginExtension = project.extensions.create("component", ComponentExtension::class.java)
        taskManager = TaskManager(p, mPluginExtension)
        mCementTransform = CementAppTransform(project)
        project.afterEvaluate {
            mPluginExtension.ensureComponentExtension(project)
            configureProject()
            createTasks()

            //if only run component task, skip some time consuming operations
            StatusManager.isRunComponentTaskOnly = isRunComponentTaskOnly()
            Log.d("component run as:${if (StatusManager.isRunComponentTaskOnly) "component" else "app"}")
            val buildType = if (StatusManager.isRunComponentTaskOnly) BuildType.COMPONENT else BuildType.APPLICATION
            if (StatusManager.isRunComponentTaskOnly) {
                mPrefixRTransform?.setPrefix(mPluginExtension.resourcePrefix)
                mCementTransform?.setTaskBuildType(buildType)
            }
        }
    }

    private fun configureProject() {
        Log.p(msg = "configure project.")
        val gradle = project.gradle
        val taskNames = gradle.startParameter.taskNames

        val needAddDependencies = needAddComponentDependencies(taskNames)

        mPluginExtension.dependencies.appendDependencies(project, needAddDependencies)
        mPluginExtension.dependencies.appendInterfaceApis(project, needAddDependencies)
    }

    private fun isRunForAar(): Boolean {
        val gradle = project.gradle
        val taskNames = gradle.startParameter.taskNames
        if (taskNames.size == 1) {
            val taskName = Descriptor.getTaskNameWithoutModule(taskNames[0])
            return taskName.startsWith("uploadComponent") ||
                    taskName.toLowerCase(Locale.getDefault()).startsWith("bundle") &&
                    taskName.toLowerCase(Locale.getDefault()).endsWith("aar")
        }
        return false
    }

    private fun isRunComponentTaskOnly(): Boolean {
        val gradle = project.gradle
        val taskNames = gradle.startParameter.taskNames
        return taskNames.size == 1 &&
                taskManager.isComponentTask(Descriptor.getTaskNameWithoutModule(taskNames[0]))
    }

    private fun needAddComponentDependencies(taskNames: List<String>): Boolean {
        taskNames.forEach {
            val taskName = Descriptor.getTaskNameWithoutModule(it)
            if (taskName.startsWith("assemble") || taskName.startsWith("install")) {
                return true
            }
        }
        return false
    }

    private fun createTasks() {
        Log.p(msg = "create tasks.")
        if (isRunForAar()) {
            val libPlugin = project.plugins.getPlugin(LibraryPlugin::class.java) as BasePlugin<*>
            val variantManager = libPlugin.variantManager

            variantManager.variantScopes.forEach {
                val variantType = it.variantData.type
                if (variantType.isTestComponent) {
                    //这里是continue,不给test的variant创建task
                    return@forEach
                }

                val taskContainer = PluginTaskContainer()
                taskManager.pluginTaskContainer = taskContainer

                taskManager.createPrefixResourcesTask(it)

                taskManager.createGenerateSymbolTask(it)

                taskManager.createRefineManifestTask(it)

//                taskManager.createReplaceManifestTask(pluginVariantScope)
//
                taskManager.crateGenInterfaceArtifactTask(it)
//
                taskManager.createUploadTask(it)
            }
        } else {
//            val appPlugin = project.plugins.getPlugin(AppPlugin::class.java) as BasePlugin<*>
//            val variantManager = appPlugin.variantManager
//
//            variantManager.variantScopes.forEach {
//                val variantType = it.variantData.type
//                if (variantType.isTestComponent) {
//                    //这里是continue,不给test的variant创建task
//                    return@forEach
//                }
//
//                it.processResourcesTask.doLast { _ ->
//                    val outDir = it.processResourcesTask.sourceOutputDir
//                    RFileAction.removeRFileFinalModifier(outDir)
//                }
//
//                taskManager.createPrefixResourcesTask(pluginVariantScope)
//
//                taskManager.createGenerateSymbolTask(pluginVariantScope)
//
//                taskManager.createRefineManifestTask(pluginVariantScope)
//
//                taskManager.createReplaceManifestTask(pluginVariantScope)
//
//                taskManager.createBundleTask(pluginVariantScope)
//
//                taskManager.crateGenInterfaceArtifactTask(pluginVariantScope)
//
//                taskManager.createUploadTask(pluginVariantScope)
//            }
        }
    }
}