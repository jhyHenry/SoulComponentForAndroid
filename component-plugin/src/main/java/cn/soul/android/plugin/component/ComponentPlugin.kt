package cn.soul.android.plugin.component

import com.android.SdkConstants.FN_CLASSES_JAR
import com.android.SdkConstants.FN_INTERMEDIATE_RES_JAR
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
import java.io.File

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
                    GradleBuildProfileSpan.ExecutionType.BASE_PLUGIN_PROJECT_BASE_EXTENSION_CREATION,
                    project.path,
                    null,
                    this::configureExtension
            )
            threadRecorder.record(
                    GradleBuildProfileSpan.ExecutionType.BASE_PLUGIN_PROJECT_TASKS_CREATION,
                    project.path,
                    null,
                    this::createTasks
            )
        }
    }

    private fun configureProject() {
        val gradle = project.gradle

    }

    private fun configureExtension() {
        val appPlugin = project.plugins.getPlugin(AppPlugin::class.java) as BasePlugin<*>
        val variantManager = appPlugin.variantManager
        val scope = variantManager.variantScopes[0]
        val realScope = scope.globalScope
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
        extension = scope.globalScope.extension as BaseExtension
        globalScope?.extension = extension
    }

    private fun createTasks() {
        val appPlugin = project.plugins.getPlugin(AppPlugin::class.java) as BasePlugin<*>
        val variantManager = appPlugin.variantManager
        variantManager.variantScopes.forEach {
            val variantType = it.variantData.type
            if (variantType.isTestComponent) {
                //这里是continue,不给test的variant创建task
                return@forEach
            }
            val pluginVariantScope = PluginVariantScopeImpl(it, globalScope!!, extension)

            taskManager.createAnchorTasks(pluginVariantScope)

            taskManager.createCheckManifestTask(pluginVariantScope)

            taskManager.createAidlTask(pluginVariantScope)

            taskManager.createJavacTask(pluginVariantScope)

            taskManager.createMergeResourcesTask(pluginVariantScope)

            taskManager.createBundleTask(pluginVariantScope)
            taskManager.addJavacClassesStream(pluginVariantScope)

            val transformManager = it.transformManager


            val jarOutputFolder = pluginVariantScope.getIntermediateJarOutputFolder()
            val mainClassJar = File(jarOutputFolder, FN_CLASSES_JAR)
            val mainResJar = File(jarOutputFolder, FN_INTERMEDIATE_RES_JAR)


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