package cn.soul.android.plugin.component

import cn.soul.android.plugin.component.extesion.ComponentExtension
import cn.soul.android.plugin.component.manager.StatusManager
import cn.soul.android.plugin.component.tasks.transform.CementAppTransform
import cn.soul.android.plugin.component.tasks.transform.CementLibTransform
import cn.soul.android.plugin.component.tasks.transform.PrefixRTransform
import cn.soul.android.plugin.component.tasks.transform.ReleaseRTransform
import cn.soul.android.plugin.component.utils.Descriptor
import cn.soul.android.plugin.component.utils.Log
import com.android.build.gradle.AppPlugin
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

    override fun apply(p: Project) {
        project = p
        Log.p("apply component plugin. ")
        p.plugins.apply("maven")
        if (isRunForAar()) {
            p.plugins.apply("com.android.library")
            val extension = project.extensions.findByType(BaseExtension::class.java)
            extension?.registerTransform(ReleaseRTransform(project))
            extension?.registerTransform(CementLibTransform(project))
        } else {
            p.plugins.apply("com.android.application")
            val extension = project.extensions.findByType(BaseExtension::class.java)
            extension?.registerTransform(CementAppTransform(project))
        }
        mPluginExtension = project.extensions.create("component", ComponentExtension::class.java)
        taskManager = TaskManager(p, mPluginExtension)
        project.afterEvaluate {
            mPluginExtension.ensureComponentExtension(project)
            configureProject()
            createTasks()

            //if only run component task, skip some time consuming operations
            StatusManager.isRunComponentTaskOnly = isRunComponentTaskOnly()
            Log.d("component run as:${if (StatusManager.isRunComponentTaskOnly) "component" else "app"}")
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
            val module = Descriptor.getTaskModuleName(taskNames[0])
            if (module != project.name) {
                return false
            }
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

    @Suppress("MISSING_DEPENDENCY_CLASS")
    private fun createTasks() {
        Log.p(msg = "create tasks.")
        if (isRunForAar()) {
            val libPlugin = project.plugins.getPlugin(LibraryPlugin::class.java) as BasePlugin<*>
            val variantManager = libPlugin.variantManager
            variantManager.variantScopes.forEach {
                //cannot access class, is a bug of kotlin plugin. issue track :
                //https://youtrack.jetbrains.com/issue/KT-26535?_ga=2.269032241.1117822405.1574306246-1707679741.1559701832
                val variantType = it.variantData.type
                if (variantType.isTestComponent) {
                    //这里是continue,不给test的variant创建task
                    return@forEach
                }

                val taskContainer = PluginTaskContainer()
                taskManager.pluginTaskContainer = taskContainer

                taskManager.createPrefixResourcesTask(it)

//                taskManager.createGenerateSymbolTask(it)

                taskManager.createRefineManifestTask(it)

                taskManager.crateGenInterfaceArtifactTask(it)

                taskManager.createUploadTask(it)
            }
        } else {
            val appPlugin = project.plugins.getPlugin(AppPlugin::class.java) as BasePlugin<*>
            val variantManager = appPlugin.variantManager
            variantManager.variantScopes.forEach {

                //                taskManager.createReplaceManifestTask(pluginVariantScope)
            }
        }
    }
}