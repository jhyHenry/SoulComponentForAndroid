package cn.soul.android.plugin.component.tasks

import cn.soul.android.plugin.component.PluginVariantScope
import cn.soul.android.plugin.component.custom.BitmapPrefix
import cn.soul.android.plugin.component.custom.IElementPrefix
import cn.soul.android.plugin.component.custom.SelectorPrefix
import cn.soul.android.plugin.component.utils.Log
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import org.dom4j.Attribute
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
    private val prefixHandleMap: MutableMap<String, IElementPrefix> = mutableMapOf()
    private var valuesCount = 0
    private val typeList = listOf(
//            "style",
//            "styleable",
//            "bool",
//            "attr",
            "string",
            "mipmap",
            "layout",
            "integer",
            "id",
            "+id",
            "drawable",
            "dimen",
            "color",
            "array",
            "anim")

    @TaskAction
    fun taskAction() {
        val startTime = System.currentTimeMillis()
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
        Log.i("prefix resources cost: ${System.currentTimeMillis() - startTime}ms")
    }

    fun putNodePrefix(elementPrefix: IElementPrefix) {
        prefixHandleMap[elementPrefix.elementName()] = elementPrefix
    }

    private fun refineXmlFile(xmlFile: File) {
        val document = reader.read(xmlFile)
        val root = document.rootElement

        if (root.name == "resources") {
            //process values.xml
            prefixResources(root)
        }
        elementTraversal(root) {
            val elementPrefix = prefixHandleMap[it.name] ?: return@elementTraversal true
            Log.e("${xmlFile.name}:$it")
            val subElementPath = elementPrefix.subElementPath()
            if (subElementPath != "") {
                val subElement = it.element(subElementPath)
                Log.e(subElement.name)
                prefixElement(subElement, elementPrefix)
                return@elementTraversal false
            } else {
                prefixElement(it, elementPrefix)
                return@elementTraversal true
            }
        }

        FileWriter(xmlFile).use {
            XMLWriter(it).apply {
                write(root)
            }
        }
    }

    private fun prefixElement(element: Element, elementPrefix: IElementPrefix) {
        element.attributeIterator().forEach { attr ->
            if (attrNeedPrefix(attr, elementPrefix)) {
                attr.value = prefixReferenceText(attr.value, elementPrefix)
            }
        }
    }

    private fun attrNeedPrefix(attr: Attribute, elementPrefix: IElementPrefix): Boolean {
        elementPrefix.targetAttrQNameList().forEach {
            if (attr.qName == it) {
                return true
            }
        }
        return false
    }

    private fun elementTraversal(root: Element, callback: (Element) -> Boolean) {
        if (!callback.invoke(root)) {
            return
        }
        root.elementIterator().forEach {
            elementTraversal(it, callback)
        }
    }

    private fun prefixResources(root: Element) {
        root.elementIterator().forEach {
            valuesCount++
            if (it.name == "declare-styleable") {
                return@forEach
            }
            val attribute = it.attribute("name")
            attribute.value = prefix + attribute.value
            if (it.text.startsWith('@')) {
                it.text = prefixElementText(it.text)
                println(it.text)
            }
        }
    }

    private fun prefixElementText(text: String): String {
        typeList.forEach {
            if (text.startsWith("@$it/")) {
                return "@$it/$prefix${text.substring(it.length + 2)}"
            }
        }
        return text
    }

    private fun prefixReferenceText(text: String, elementPrefix: IElementPrefix): String {
        typeList.forEach {
            if (text.startsWith("@$it/")) {
                return elementPrefix.prefix("@$it/", text.substring(it.length + 2), prefix)
            }
        }
        return text
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
            val list = mutableListOf<IElementPrefix>()
            list.add(BitmapPrefix())
            list.add(SelectorPrefix())
            list.forEach {
                task.putNodePrefix(it)
            }
        }
    }
}