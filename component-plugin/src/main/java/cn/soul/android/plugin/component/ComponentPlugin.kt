package cn.soul.android.plugin.component

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.profile.Recorder
import com.android.builder.profile.ThreadRecorder
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * @author panxinghai
 *
 * date : 2019-07-11 11:19
 */
class ComponentPlugin : Plugin<Project> {
    private lateinit var extension: BaseExtension
    private lateinit var project: Project
    private lateinit var projectOptions: ProjectOptions
    private lateinit var threadRecorder: Recorder
    private lateinit var taskManager: TaskManager

    companion object {
        val ANDROID_PLUGIN_VERSION = "3.2.1"
    }

    override fun apply(p: Project) {
        project = p
        projectOptions = ProjectOptions(p)
        threadRecorder = ThreadRecorder.get()
        taskManager = TaskManager(p)
        project.afterEvaluate {
            threadRecorder.record(
                GradleBuildProfileSpan.ExecutionType.BASE_PLUGIN_PROJECT_CONFIGURE,
                project.path,
                null,
                this::configureProject
            )
            threadRecorder.record(
                GradleBuildProfileSpan.ExecutionType.BASE_PLUGIN_PROJECT_CONFIGURE,
                project.path,
                null,
                this::configureExtension
            )
        }
    }

    private fun configureProject() {
        val gradle = project.gradle

    }

    private fun configureExtension() {
        extension = project.extensions.getByName("android") as BaseExtension
        val appPlugin = project.plugins.getPlugin(AppPlugin::class.java) as BasePlugin<*>
        println(appPlugin)
        val variantManager = appPlugin.variantManager
        println(variantManager)
        variantManager.variantScopes.forEach {
            val variantType = it.variantData.type
            if (variantType.isTestComponent) {
                //这里是continue
                return@forEach
            }
            val pluginVariantScope = PluginVariantScopeImpl(it)
            taskManager.createCheckManifestTask(pluginVariantScope)
        }
    }
}