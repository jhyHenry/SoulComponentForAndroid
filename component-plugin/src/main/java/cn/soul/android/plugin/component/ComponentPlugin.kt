package cn.soul.android.plugin.component

import cn.soul.android.plugin.component.extesion.ComponentExtension
import cn.soul.android.plugin.component.tasks.transform.CementAppTransform
import cn.soul.android.plugin.component.tasks.transform.CementLibTransform
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
    private lateinit var mProject: Project
    private lateinit var mTaskManager: TaskManager

    private var mLibConfigExecutor: (() -> Unit)? = null
    override fun apply(p: Project) {
        mProject = p
        Log.p("apply component plugin. ")
        p.plugins.apply("maven")
        if (isRunForAar()) {
            mProject.afterEvaluate {
                mLibConfigExecutor?.invoke()
            }
            p.plugins.apply("com.android.library")
            val extension = mProject.extensions.findByType(BaseExtension::class.java)
            extension?.registerTransform(CementLibTransform(mProject))
            mLibConfigExecutor = {
                extension?.apply {
                    defaultConfig.applicationId = null
                    buildTypes {
                        it.all { buildType ->
                            buildType.isShrinkResources = false
                        }
                    }
                }
            }
        } else {
            p.plugins.apply("com.android.application")
            val extension = mProject.extensions.findByType(BaseExtension::class.java)
            extension?.registerTransform(CementAppTransform(mProject))
        }
        mPluginExtension = mProject.extensions.create("component", ComponentExtension::class.java)
        mTaskManager = TaskManager(p, mPluginExtension)
        mProject.afterEvaluate {
            mPluginExtension.ensureComponentExtension(mProject)
            configureProject()
            createTasks()
        }
    }

    private fun configureProject() {
        Log.p(msg = "configure project.")
        val gradle = mProject.gradle
        val taskNames = gradle.startParameter.taskNames

        val needAddDependencies = needAddComponentDependencies(taskNames)

        mPluginExtension.dependencies.appendDependencies(mProject, needAddDependencies)
        mPluginExtension.dependencies.appendInterfaceApis(mProject, needAddDependencies)
    }

    private fun isRunForAar(): Boolean {
        val gradle = mProject.gradle
        val taskNames = gradle.startParameter.taskNames
        if (taskNames.size == 1) {
            val module = Descriptor.getTaskModuleName(taskNames[0])
            if (module != mProject.name) {
                return false
            }
            val taskName = Descriptor.getTaskNameWithoutModule(taskNames[0])
            return taskName.startsWith("uploadComponent") ||
                    taskName.toLowerCase(Locale.getDefault()).startsWith("bundle") &&
                    taskName.toLowerCase(Locale.getDefault()).endsWith("aar")
        }
        return false
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
            val libPlugin = mProject.plugins.getPlugin(LibraryPlugin::class.java) as BasePlugin<*>
            val variantManager = libPlugin.variantManager
            variantManager.variantScopes.forEach {
                //cannot access class, is a bug of kotlin plugin. issue track :
                //https://youtrack.jetbrains.com/issue/KT-26535?_ga=2.269032241.1117822405.1574306246-1707679741.1559701832
                val variantType = it.variantData.type
                if (variantType.isTestComponent) {
                    //continue , do not create task for test variant
                    return@forEach
                }

                val taskContainer = PluginTaskContainer()
                mTaskManager.pluginTaskContainer = taskContainer

                mTaskManager.createPrefixResourcesTask(it)

                mTaskManager.createGenerateSymbolTask(it)

                mTaskManager.createRefineManifestTask(it)

                mTaskManager.crateGenInterfaceArtifactTask(it)

                mTaskManager.createUploadTask(it)

                mTaskManager.applyProguard(mProject, it)
            }
        } else {
            val appPlugin = mProject.plugins.getPlugin(AppPlugin::class.java) as BasePlugin<*>
            val variantManager = appPlugin.variantManager
            variantManager.variantScopes.forEach {
//                mTaskManager.createReplaceManifestTask(it)
//
//                mTaskManager.applyProguard(mProject, it)
            }
        }
    }
}