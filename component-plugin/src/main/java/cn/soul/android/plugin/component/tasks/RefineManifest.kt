package cn.soul.android.plugin.component.tasks

import cn.soul.android.plugin.component.PluginVariantScope
import cn.soul.android.plugin.component.utils.AndroidXmlHelper
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.google.common.collect.ImmutableList
import org.dom4j.QName
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileWriter

/**
 * @author panxinghai
 *
 * date : 2019-07-18 15:20
 */
open class RefineManifest : AndroidVariantTask() {
    var manifestFile: File? = null

    @TaskAction
    fun taskAction() {
        val reader = SAXReader()
        val document = reader.read(manifestFile)
        val root = document.rootElement

        //remove versionCode and versionName attribute in <manifest>
        root.remove(root.attribute(AndroidXmlHelper.getQName("versionCode")))
        root.remove(root.attribute(AndroidXmlHelper.getQName("versionName")))

        //remove uses-sdk tag
        root.remove(root.element("uses-sdk"))

        val applicationElement = root.element(AndroidXmlHelper.TAG_APPLICATION)
        //remove application all attributes
        val attributeNameList = mutableListOf<QName>()
        applicationElement.attributes().forEach {
            attributeNameList.add(it.qName)
        }
        attributeNameList.forEach {
            applicationElement.remove(applicationElement.attribute(it))
        }

        //remove action and category that equals MAIN/LAUNCHER in <intent-filter>
        applicationElement.elementIterator(AndroidXmlHelper.TAG_ACTIVITY).forEach { activityElement ->
            activityElement.elementIterator("intent-filter").forEach { element ->
                element.elementIterator("action").forEach {
                    val attribute = it.attribute(AndroidXmlHelper.getQName("name"))
                    if (attribute.value == AndroidXmlHelper.ACTION_MAIN) {
                        element.remove(it)
                    }
                }
                element.elementIterator("category").forEach {
                    val attribute = it.attribute(AndroidXmlHelper.getQName("name"))
                    if (attribute.value == AndroidXmlHelper.CATEGORY_LAUNCHER) {
                        element.remove(it)
                    }
                }
            }
        }

        applicationElement.elementIterator(AndroidXmlHelper.TAG_ACTIVITY).forEach { activityElement ->
            val intentFilterElement = activityElement.element("intent-filter")
            if (intentFilterElement.attributeCount() == 0) {
                activityElement.remove(intentFilterElement)
            }
        }
        FileWriter(manifestFile!!).use {
            XMLWriter(it).apply {
                write(root)
            }
        }

    }

    class ConfigAction(private val scope: PluginVariantScope,
                       private val manifestFile: File) : TaskConfigAction<RefineManifest> {
        override fun getName(): String {
            return scope.getTaskName("Refine", "Manifest")
        }

        override fun getType(): Class<RefineManifest> {
            return RefineManifest::class.java
        }

        override fun execute(task: RefineManifest) {
            task.manifestFile = manifestFile
            task.variantName = scope.getFullName()

            scope.getTaskContainer().pluginRefineManifest = task
            task.dependsOn(scope.getTaskContainer().pluginProcessManifest)

            scope.getArtifacts()
                    .appendArtifact(
                            InternalArtifactType.LIBRARY_MANIFEST,
                            ImmutableList.of(manifestFile),
                            task)
        }
    }
}