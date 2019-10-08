package cn.soul.android.plugin.component

import cn.soul.android.plugin.component.action.RFileAction
import cn.soul.android.plugin.component.extesion.ComponentExtension
import cn.soul.android.plugin.component.manager.BuildType
import cn.soul.android.plugin.component.manager.StatusManager
import cn.soul.android.plugin.component.tasks.transform.PrefixRTransform
import cn.soul.android.plugin.component.tasks.transform.RouterCompileTransform
import cn.soul.android.plugin.component.utils.Descriptor
import cn.soul.android.plugin.component.utils.Log
import com.android.build.api.transform.Transform
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.internal.TaskManager.createAndroidJarConfig
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.profile.Recorder
import com.android.builder.profile.ThreadRecorder
import com.android.utils.StringHelper
import com.google.common.base.CaseFormat
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.stream.Collectors

/**
 * @author panxinghai
 *
 * date : 2019-07-11 11:19
 */
class ComponentPlugin : Plugin<Project> {
    private lateinit var extension: BaseExtension
    private lateinit var pluginExtension: ComponentExtension
    private lateinit var project: Project
    private lateinit var projectOptions: ProjectOptions
    private lateinit var threadRecorder: Recorder
    private lateinit var taskManager: TaskManager
    private var globalScope: GlobalScope? = null

    private var mPrefixRTransform: PrefixRTransform? = null
    private var mRouterCompileTransform: RouterCompileTransform? = null

    override fun apply(p: Project) {
        project = p
        projectOptions = ProjectOptions(p)
        threadRecorder = ThreadRecorder.get()
        taskManager = TaskManager(p)
        Log.p(msg = "apply component plugin.")
        p.plugins.apply("maven")

        pluginExtension = project.extensions.create("component", ComponentExtension::class.java)
        mRouterCompileTransform = RouterCompileTransform(project)
        mPrefixRTransform = PrefixRTransform(project)
        project.extensions.findByType(BaseExtension::class.java)?.registerTransform(mRouterCompileTransform)
        project.extensions.findByType(BaseExtension::class.java)?.registerTransform(mPrefixRTransform)
        project.afterEvaluate {
            pluginExtension.ensureComponentExtension(project)

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
            //if only run component task, skip some time consuming operations
            StatusManager.isRunComponentTaskOnly = isRunComponentTaskOnly()
            val buildType = if (StatusManager.isRunComponentTaskOnly) BuildType.COMPONENT else BuildType.APPLICATION
            if (StatusManager.isRunComponentTaskOnly) {
                mPrefixRTransform?.setPrefix(pluginExtension.resourcePrefix)
                mPrefixRTransform?.setTaskBuildType(buildType)
                mRouterCompileTransform?.setTaskBuildType(buildType)
            }
        }
    }

    private fun configureProject() {
        Log.p(msg = "configure project.")
        val gradle = project.gradle
        val taskNames = gradle.startParameter.taskNames

        val needAddDependencies = needAddComponentDependencies(taskNames)

        pluginExtension.dependencies.appendDependencies(project, needAddDependencies)
        pluginExtension.dependencies.appendInterfaceApis(project, needAddDependencies)
    }

    private fun isRunComponentTaskOnly(): Boolean {
        val gradle = project.gradle
        val taskNames = gradle.startParameter.taskNames
        return taskNames.size == 1 && taskManager.isComponentTask(taskNames[0])
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

    private fun configureExtension() {
        Log.p(msg = "configure extension.")
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
        globalScope?.setAndroidJarConfig(createAndroidJarConfig(project))
        extension = scope.globalScope.extension as BaseExtension
        globalScope?.extension = extension
    }

    private fun createTasks() {
        Log.p(msg = "create tasks.")
        val appPlugin = project.plugins.getPlugin(AppPlugin::class.java) as BasePlugin<*>
        val variantManager = appPlugin.variantManager

        variantManager.variantScopes.forEach {
            val variantType = it.variantData.type
            if (variantType.isTestComponent) {
                //这里是continue,不给test的variant创建task
                return@forEach
            }

            it.processResourcesTask.doLast { _ ->
                val outDir = it.processResourcesTask.sourceOutputDir
                RFileAction.removeRFileFinalModifier(outDir)
            }
            val transformManager = TransformManager(project, globalScope!!.errorHandler, threadRecorder)
            val pluginVariantScope = PluginVariantScopeImpl(it, globalScope!!, extension, transformManager, pluginExtension)

            taskManager.createDependencyStreams(pluginVariantScope, transformManager)

            taskManager.createAnchorTasks(pluginVariantScope)

            taskManager.createCheckManifestTask(pluginVariantScope)
            taskManager.createMergeLibManifestsTask(pluginVariantScope)

            taskManager.createAidlTask(pluginVariantScope)

            taskManager.createMergeResourcesTask(pluginVariantScope)

            taskManager.createPrefixResourcesTask(pluginVariantScope)

            taskManager.createGenerateSymbolTask(pluginVariantScope)

            val lastTransform = extension.transforms[extension.transforms.lastIndex]
            val task = project.tasks.getByName(getTransformTaskName(lastTransform, pluginVariantScope.getFullName()))

            val transformTask = task as TransformTask
            val javaOutputs = project.files(transformTask.streamOutputFolder).builtBy(transformTask)
            taskManager.addJavacClassesStream(javaOutputs, pluginVariantScope)

            taskManager.transform(pluginVariantScope)

            taskManager.createRefineManifestTask(pluginVariantScope)

            taskManager.createBundleTask(pluginVariantScope)

            taskManager.crateGenInterfaceArtifactTask(pluginVariantScope)

            taskManager.createUploadTask(pluginVariantScope)
        }
    }

    private fun getTransformTaskName(transform: Transform, variant: String): String {
        val sb = StringBuilder(100)
        sb.append("transform")
        sb.append(
                transform
                        .inputTypes
                        .stream()
                        .map { inputType ->
                            CaseFormat.UPPER_UNDERSCORE.to(
                                    CaseFormat.UPPER_CAMEL, inputType.name())
                        }
                        .sorted() // Keep the order stable.
                        .collect(Collectors.joining("And")))
        sb.append("With")
        StringHelper.appendCapitalized(sb, transform.name)
        sb.append("For")
        StringHelper.appendCapitalized(sb, variant)
        return sb.toString()
    }
}