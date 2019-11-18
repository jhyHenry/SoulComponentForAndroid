package cn.soul.android.plugin.component

import cn.soul.android.plugin.component.extesion.ComponentExtension
import cn.soul.android.plugin.component.tasks.*
import cn.soul.android.plugin.component.utils.Descriptor
import com.android.SdkConstants
import com.android.build.gradle.internal.TaskFactory
import com.android.build.gradle.internal.TaskFactoryImpl
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.VariantHelper
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File
import java.util.*

/**
 * @author panxinghai
 *
 * date : 2019-07-11 14:22
 */
class TaskManager(private val project: Project,
                  private val extension: ComponentExtension) {
    private var taskFactory: TaskFactory = PluginTaskFactory(TaskFactoryImpl(project.tasks), this)
    val componentTaskContainer: MutableSet<Task> = mutableSetOf()
    var pluginTaskContainer: PluginTaskContainer? = null

    fun isComponentTask(taskName: String): Boolean {
        componentTaskContainer.forEach {
            if (it.name == taskName) {
                return true
            }
        }
        return false
    }


    fun createRefineManifestTask(scope: VariantScope) {
        val manifestOutputDir = scope.taskContainer.processManifestTask?.manifestOutputDirectory
                ?: return
        val processManifestFile = File(manifestOutputDir, SdkConstants.FN_ANDROID_MANIFEST_XML)
        val task = taskFactory.create(RefineManifest.ConfigAction(scope, processManifestFile))
        scope.taskContainer.bundleLibraryTask?.dependsOn(task)
    }

    fun createPrefixResourcesTask(scope: VariantScope) {
        val file = scope.getIntermediateDir(InternalArtifactType.PACKAGED_RES)
        var prefix = extension.resourcePrefix
        if (prefix == null) {
            prefix = "${project.name}_"
        }
        val task = taskFactory.create(PrefixResources.ConfigAction(scope, file, prefix))
        task.dependsOn(scope.taskContainer.mergeResourcesTask)
        pluginTaskContainer?.prefixResources = task
        scope.taskContainer.bundleLibraryTask?.dependsOn(task)
    }

    fun createGenerateSymbolTask(scope: VariantScope) {
        val dir = File(scope.globalScope.intermediatesDir,
                "symbols/" + scope
                        .variantData
                        .variantConfiguration
                        .dirName)
        val symbol = File(dir, SdkConstants.FN_RESOURCE_TEXT)
        val symbolTableWithPackageName = FileUtils.join(
                scope.globalScope.intermediatesDir,
                SdkConstants.FD_RES,
                "symbol-table-with-package",
                scope.variantConfiguration.dirName,
                "package-aware-r.txt")
        val task = taskFactory.create(GenerateSymbol.ConfigAction(scope,
                symbol,
                symbolTableWithPackageName))
        scope.artifacts.replaceArtifact(
                InternalArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME,
                ImmutableList.of(symbol),
                task)
        task.dependsOn(pluginTaskContainer?.prefixResources)
        scope.taskContainer.bundleLibraryTask?.dependsOn(task)
    }

    fun crateGenInterfaceArtifactTask(scope: VariantScope) {
        val file = scope.artifacts.getFinalArtifactFiles(InternalArtifactType.JAVAC).single()
        val task = taskFactory.create(GenerateInterfaceArtifact.ConfigAction(scope, file))
        task.dependsOn(scope.taskContainer.bundleLibraryTask)
        pluginTaskContainer?.genInterface = task
        if (scope.variantConfiguration.buildType.name != "release") {
            return
        }
        val uploadTaskPrefix = "uploadComponent"
        if (project.gradle.startParameter.taskNames.size == 0) {
            return
        }
        val startTaskName = Descriptor.getTaskNameWithoutModule(project.gradle.startParameter.taskNames[0])
        if (startTaskName.startsWith(uploadTaskPrefix)) {
            val flavor = startTaskName.substring(uploadTaskPrefix.length)
            if (flavor.toLowerCase(Locale.getDefault()) == scope.variantConfiguration.flavorName) {
                project.artifacts.add("archives", File(task.destDir, "interface.jar"))
            }
        }
    }

    fun createReplaceManifestTask(scope: VariantScope) {
        val manifestFile = scope.taskContainer.packageAndroidTask?.manifests?.single()
                ?: return
        val task = taskFactory.create(ReplaceManifest.ConfigAction(scope, File(manifestFile, "AndroidManifest.xml")))
        scope.taskContainer.processAndroidResTask?.dependsOn(task)
        task.dependsOn(scope.taskContainer.processManifestTask)
    }

    fun createUploadTask(scope: VariantScope) {
        if (scope.variantConfiguration.buildType.name != "release") {
            return
        }

        val uploadTaskPrefix = "uploadComponent"
        if (project.gradle.startParameter.taskNames.size == 0) {
            return
        }
        val startTaskName = Descriptor.getTaskNameWithoutModule(project.gradle.startParameter.taskNames[0])
        if (startTaskName.startsWith(uploadTaskPrefix)) {
            val flavor = startTaskName.substring(uploadTaskPrefix.length)
            if (flavor.toLowerCase(Locale.getDefault()) == scope.variantConfiguration.flavorName) {
                VariantHelper.setupArchivesConfig(project, scope.variantDependencies.runtimeClasspath)
                project.artifacts.add("archives", scope.taskContainer.bundleLibraryTask!!)
            }
        }
        val task = taskFactory.create(UploadComponent.ConfigAction(scope, project))
        pluginTaskContainer?.uploadTask = task
        task.dependsOn(scope.taskContainer.bundleLibraryTask)
        task.dependsOn(pluginTaskContainer?.genInterface!!)
    }
}