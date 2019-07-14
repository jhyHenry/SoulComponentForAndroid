package cn.soul.android.plugin.component

import com.android.build.gradle.*
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.scope.DelayedActionsExecutor
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.variant2.DslScopeImpl
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
    private var globalScope: GlobalScope? = null

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
        val appExtension = project.extensions.getByName("android") as AppExtension

        val appPlugin = project.plugins.getPlugin(AppPlugin::class.java) as BasePlugin<*>
        val variantManager = appPlugin.variantManager
        variantManager.variantScopes.forEach {
            val variantType = it.variantData.type
            if (variantType.isTestComponent) {
                //这里是continue,不给test的variant创建task
                return@forEach
            }
            if (globalScope == null) {
                val realScope = it.globalScope
                globalScope = GlobalScope(
                        realScope.project,
                        realScope.filesProvider,
                        realScope.projectOptions,
                        realScope.dslScope,
                        realScope.androidBuilder,
                        realScope.sdkHandler,
                        realScope.toolingRegistry,
                        realScope.buildCache
                )
                val extraModelInfo = ExtraModelInfo(project.path, projectOptions, project.logger)
                extension = LibraryExtension(project, projectOptions,
                        globalScope,
                        globalScope?.sdkHandler,
                        appExtension.buildTypes,
                        appExtension.productFlavors,
                        appExtension.signingConfigs,
                        appExtension.buildOutputs,
                        createSourceSetManager(extraModelInfo),
                        extraModelInfo)

                globalScope?.extension = extension
            }
            val pluginVariantScope = PluginVariantScopeImpl(it, globalScope!!, extension)

            taskManager.createAnchorTasks(pluginVariantScope)

            taskManager.createCheckManifestTask(pluginVariantScope)

            taskManager.createAidlTask(pluginVariantScope)

            taskManager.createJavacTask(pluginVariantScope)

            taskManager.createMergeResourcesTask(pluginVariantScope)

            taskManager.createBundleTask(pluginVariantScope)
        }
    }

    private fun createSourceSetManager(extraModelInfo: ExtraModelInfo): SourceSetManager {
        return SourceSetManager(
                project,
                //library为true
                true,
                DslScopeImpl(
                        extraModelInfo.syncIssueHandler,
                        extraModelInfo.deprecationReporter,
                        project.objects),
                DelayedActionsExecutor())
    }
}