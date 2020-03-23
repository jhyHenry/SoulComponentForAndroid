package cn.soul.android.plugin.component

import cn.soul.android.plugin.component.extesion.ComponentExtension
import cn.soul.android.plugin.component.resolve.DuplicateHelper
import cn.soul.android.plugin.component.resolve.ZipHelper
import cn.soul.android.plugin.component.resolve.arsc.ArscFile
import cn.soul.android.plugin.component.resolve.arsc.StringPoolChunk
import cn.soul.android.plugin.component.resolve.arsc.TableChunk
import cn.soul.android.plugin.component.tasks.transform.KhalaAppTransform
import cn.soul.android.plugin.component.tasks.transform.KhalaLibTransform
import cn.soul.android.plugin.component.utils.Descriptor
import cn.soul.android.plugin.component.utils.Log
import com.android.build.gradle.*
import com.android.build.gradle.internal.api.ApplicationVariantImpl
import net.sf.json.JSONArray
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.*
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
            extension?.registerTransform(KhalaLibTransform(mProject))
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
            val extension = mProject.extensions.findByType(AppExtension::class.java)
            extension?.registerTransform(KhalaAppTransform(mProject))
            extension?.applicationVariants?.all {
                if (it.buildType.name != "release") {
                    return@all
                }
                val variantData = (it as ApplicationVariantImpl).variantData
                val task = variantData.taskContainer.packageAndroidTask
                task?.get()?.doFirst {
                    val resourceArtifact = task.get().resourceFiles
                    resourceArtifact.files.forEach { file ->
                        replaceDuplicateResource(file)
                    }
                }
            }
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

                mTaskManager.createGenInterfaceArtifactTask(it)

                mTaskManager.createUploadTask(it)

                mTaskManager.applyProguard(mProject, it)
            }
        } else {
            val appPlugin = mProject.plugins.getPlugin(AppPlugin::class.java) as BasePlugin<*>
            val variantManager = appPlugin.variantManager
            variantManager.variantScopes.forEach {
                //                mTaskManager.createReplaceManifestTask(it)

                mTaskManager.applyProguard(mProject, it)
            }
        }
    }

    private fun replaceDuplicateResource(file: File) {
        val apFileName = readJsonFileAndGetPathValue(File(file, "output.json"))
        val zipFile = File(file, apFileName)
        var arscFile: File? = null
        val resourceFile: ArscFile?
        try {
            if (!zipFile.exists()) {
                println("file$zipFile not exist")
                return
            }
            arscFile = ZipHelper.unzipSpecialFile(zipFile, "resources.arsc", zipFile.parent)
            val duplicateCollection = DuplicateHelper.checkDuplicate(zipFile)
            val replaceTargetMap = mutableMapOf<String, String>()
            duplicateCollection.forEach {
                val target = it.value[0]
                for (i in 1 until it.value.size) {
                    replaceTargetMap[it.value[i]] = target
                    ZipHelper.removeZipEntry(zipFile, it.value[i])
                }
            }
            resourceFile = ArscFile.fromFile(arscFile)
            val chunks = resourceFile.chunks
            chunks.forEach {
                if (it is TableChunk) {
                    val stringPool = it.chunks[0] as StringPoolChunk
                    for (i in 0 until stringPool.stringCount) {
                        val key = stringPool.getString(i)
                        if (replaceTargetMap.containsKey(key)) {
                            replaceTargetMap[key]?.let { it1 -> stringPool.setString(i, it1) }
//                            println("replace $key to ${replaceTargetMap[key]}")
                        }
                    }
                }
            }

            arscFile.delete()
            arscFile = File(file, "resources.arsc")
            arscFile.createNewFile()

            val fis = FileOutputStream(arscFile)
            fis.use {
                fis.write(resourceFile.toByteArray())
            }

            ZipHelper.addZipEntry(zipFile, arscFile, "resources.arsc")
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            arscFile?.delete()
        }

    }

    private fun readPlaintTextFile(file: File): String {
        val bufferedReader: BufferedReader
        val sb = StringBuilder()
        var tempString: String?
        val fileInputStream = FileInputStream(file)
        val inputStreamReader = InputStreamReader(fileInputStream, "UTF-8")
        bufferedReader = BufferedReader(inputStreamReader)
        bufferedReader.use { reader ->
            while (reader.readLine().also { tempString = it } != null) {
                sb.append(tempString)
            }
        }
        return sb.toString()
    }

    @Throws(IOException::class)
    fun readJsonFileAndGetPathValue(file: File): String {
        val jsonString = readPlaintTextFile(file)
        val jsonArray = JSONArray.fromObject(jsonString)
        val jsonObject = jsonArray.getJSONObject(0)
        return jsonObject.getString("path")
    }
}