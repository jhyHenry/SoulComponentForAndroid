package cn.soul.android.plugin.component

import cn.soul.android.plugin.component.action.RFileAction
import cn.soul.android.plugin.component.extesion.ComponentExtension
import cn.soul.android.plugin.component.manager.BuildType
import cn.soul.android.plugin.component.manager.StatusManager
import cn.soul.android.plugin.component.tasks.transform.CementAppTransform
import cn.soul.android.plugin.component.tasks.transform.PrefixRTransform
import cn.soul.android.plugin.component.tasks.transform.RouterCompileTransform
import cn.soul.android.plugin.component.utils.Descriptor
import cn.soul.android.plugin.component.utils.Log
import com.android.build.api.transform.Transform
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.utils.StringHelper
import com.google.common.base.CaseFormat
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.*
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
    private lateinit var taskManager: TaskManager

    private var mPrefixRTransform: PrefixRTransform? = null
    private var mRouterCompileTransform: RouterCompileTransform? = null
    private var mCementTransform: CementAppTransform? = null

    override fun apply(p: Project) {
        project = p
        taskManager = TaskManager(p)
        Log.p("apply component plugin. ")
        p.plugins.apply("maven")
        if (isRunForAar()) {
            p.plugins.apply("com.android.library")
        } else {
            p.plugins.apply("com.android.application")
        }

        pluginExtension = project.extensions.create("component", ComponentExtension::class.java)
        mRouterCompileTransform = RouterCompileTransform(project)
        mPrefixRTransform = PrefixRTransform(project)
        mCementTransform = CementAppTransform(project)
        project.extensions.findByType(BaseExtension::class.java)?.registerTransform(mRouterCompileTransform)
        project.extensions.findByType(BaseExtension::class.java)?.registerTransform(mCementTransform)
        project.afterEvaluate {
            pluginExtension.ensureComponentExtension(project)
            configureProject()

            //if only run component task, skip some time consuming operations
            StatusManager.isRunComponentTaskOnly = isRunComponentTaskOnly()
            Log.d("component run as:${if (StatusManager.isRunComponentTaskOnly) "component" else "app"}")
            val buildType = if (StatusManager.isRunComponentTaskOnly) BuildType.COMPONENT else BuildType.APPLICATION
            if (StatusManager.isRunComponentTaskOnly) {
                mPrefixRTransform?.setPrefix(pluginExtension.resourcePrefix)
                mPrefixRTransform?.setTaskBuildType(buildType)
                mRouterCompileTransform?.setTaskBuildType(buildType)
                mCementTransform?.setTaskBuildType(buildType)
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

    private fun isRunForAar(): Boolean {
        val gradle = project.gradle
        val taskNames = gradle.startParameter.taskNames
        if (taskNames.size == 1) {
            val taskName = Descriptor.getTaskNameWithoutModule(taskNames[0])
            return taskName == "uploadComponent" ||
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

            taskManager.createLibraryAssetsTask(pluginVariantScope)

            val lastTransform = extension.transforms[extension.transforms.lastIndex]
            val task = project.tasks.getByName(getTransformTaskName(lastTransform, pluginVariantScope.getFullName()))

            val transformTask = task as TransformTask
            val javaOutputs = project.files(transformTask.streamOutputFolder).builtBy(transformTask)

            taskManager.addJavacClassesStream(javaOutputs, pluginVariantScope)

            taskManager.createFilterClassTransform(pluginVariantScope, transformManager)

            val prefixRTransform = mPrefixRTransform
            if (prefixRTransform != null) {
                taskManager.addPluginTransform(pluginVariantScope, prefixRTransform)
            }

            taskManager.transform(pluginVariantScope)

            taskManager.createRefineManifestTask(pluginVariantScope)

            taskManager.createReplaceManifestTask(pluginVariantScope)

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