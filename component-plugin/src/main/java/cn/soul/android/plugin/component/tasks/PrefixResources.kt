package cn.soul.android.plugin.component.tasks

import cn.soul.android.plugin.component.PluginVariantScope
import cn.soul.android.plugin.component.utils.AndroidXmlHelper
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import org.dom4j.Element
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileWriter

/**
 * Created by nebula on 2019-08-15
 */
open class PrefixResources : AndroidVariantTask() {
    var packagedResFolder: File? = null
    var prefix: String = ""
    private val reader: SAXReader = SAXReader()

    @TaskAction
    fun taskAction() {
        val folder = packagedResFolder ?: return
        folder.listFiles()?.forEach {
            if (it.isDirectory) {
                it.listFiles()?.forEach { subFile ->
                    if (subFile.name.endsWith(".xml")) {
                        refineXmlFile(subFile)
                    }
                }
            } else if (it.name.endsWith(".xml")) {
                refineXmlFile(it)
            }
        }
    }

    private fun refineXmlFile(xmlFile: File) {
        val document = reader.read(xmlFile)
        val root = document.rootElement

        when (root.name) {
            "resources" -> {
                //process values.xml
                prefixResources(root)
            }
            else -> return
        }
        FileWriter(xmlFile).use {
            XMLWriter(it).apply {
                write(root)
            }
        }
    }

    private fun prefixSelector(root: Element) {

    }

    private fun prefixResources(root: Element) {
        root.elementIterator().forEach {
            if (it.name == "declare-styleable") {
                return@forEach
            }
            val attribute = it.attribute("name")
            attribute.value = prefix + attribute.value
            println(attribute.stringValue)
        }
    }

    class ConfigAction(private val scope: PluginVariantScope,
                       private val packagedResFolder: File,
                       private val prefix: String) : TaskConfigAction<PrefixResources> {
        override fun getType(): Class<PrefixResources> {
            return PrefixResources::class.java
        }

        override fun getName(): String {
            return scope.getTaskName("prefix", "Resources")
        }

        override fun execute(task: PrefixResources) {
            task.variantName = scope.fullVariantName
            task.packagedResFolder = packagedResFolder
            task.prefix = prefix
        }
    }
}