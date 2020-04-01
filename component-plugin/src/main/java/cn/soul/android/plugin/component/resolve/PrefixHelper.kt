package cn.soul.android.plugin.component.resolve

import cn.soul.android.plugin.component.utils.Log
import org.dom4j.Element
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import java.io.File
import java.io.FileWriter
import java.util.*
import kotlin.collections.HashSet

/**
 * @author panxinghai
 *
 * date : 2019-09-06 14:06
 */
class PrefixHelper {
    companion object {
        val instance: PrefixHelper by lazy {
            PrefixHelper()
        }
    }

    private val resTypeSet = hashSetOf("anim",
            "animator",
            "color",
            "drawable",
            "font",
            "interpolator",
            "layout",
            "menu",
            "mipmap",
            "navigation",
            "raw",
            "transition",
            "values",
            "xml"
    )

    private val accessTypeSet = hashSetOf(
            "style",
//            "styleable",
//            "attr",
            "bool",
            "string",
            "plurals",
            "layout",
            "integer",
            "id",
            "+id",
            "dimen",
            "array"
    )

    private val attributeNameTypeMap = hashMapOf(
            "string-array" to "array")

    private val componentResMap = hashMapOf<String, HashSet<String>>()
    private val reader: SAXReader = SAXReader()
    var prefix = ""

    fun initWithPackagedRes(prefix: String, dir: File) {
        this.prefix = prefix
        componentResMap.clear()
        accessTypeSet.addAll(resTypeSet)
        require(dir.parent != "packaged_res") {
            "error dir, prefixHelper must receive packaged_res/\$variantName dir."
        }
        dir.walk().filter { it.isFile && it.name != "values.xml" }
                .forEach {

                    //obtain res type, split name because of Android resources dimens. eg:<resources_name>-<config_qualifier>
                    val type = it.parentFile.name.split('-')[0]
                    componentResMap.computeIfAbsent(type) {
                        hashSetOf()
                    }.add(it.name.split('.')[0])
                    it.renameTo(File(it.parentFile, prefix + it.name))
                }
        val document = reader.read(File(dir, "values/values.xml"))
        val root = document.rootElement
        root.elementIterator().forEach {
            var type = it.name
            //handle special type. eg:[string-array]
            if (attributeNameTypeMap.containsKey(type)) {
                type = attributeNameTypeMap[type]
            }
            if (!accessTypeSet.contains(type)) {
                return@forEach
            }
            val attribute = it.attribute("name")
            componentResMap.computeIfAbsent(type) {
                hashSetOf()
            }.add(attribute.value)
        }
        componentResMap.forEach {
            Log.d("${it.key}:")
            it.value.forEach { value ->
                Log.d("\t $value")
            }
        }
        Log.d("end")
    }

    fun prefixResourceFile(file: File) {
        Log.d("prefix: ${file.name}")
        if (file.name.split('.')[1] != "xml") {
            return
        }
        val document = reader.read(file)
        val element = document.rootElement
        prefixResourceFile(element)
        writeFile(file, element)
    }

    fun prefixResourceFile(root: Element) {
        elementTraversal(root) {
            it.attributes().forEach { attr ->
                if (attr.text.startsWith('@')) {
                    attr.text = prefixElementText(attr.text)
                }
            }
            return@elementTraversal true
        }
    }

    fun prefixValues(file: File) {
        val document = reader.read(file)
        val element = document.rootElement
        if (element.name != "resources") {
            Log.e("wrong values file: ${file.absolutePath}, skip prefix.")
            return
        }
        element.elementIterator().forEach {
            if (!accessTypeSet.contains(it.name)) {
                return@forEach
            }
            val attribute = it.attribute("name")
            attribute.value = prefix + attribute.value
            if (it.text.startsWith('@')) {
                it.text = prefixElementText(it.text)
            }
        }
        writeFile(file, element)
    }

    /**
     * prefix resources reference in xml file.
     * @param text resources reference, must starts with '@'
     */
    private fun prefixElementText(text: String): String {
        val strings = text.split('/')
        val type = strings[0].substring(1)
        if (strings.size == 1 && type.toLowerCase(Locale.getDefault()) == "null") {
            return text
        }
        val resourceRef = strings[1]
        if (!accessTypeSet.contains(type)) {
            return text
        }
        //if resource did not in current component, do not add prefix for this resource reference
        if (!isRefNeedPrefix(type, resourceRef)) {
            return text
        }
        return "@$type/$prefix${resourceRef}"
    }

    fun isRefNeedPrefix(type: String, ref: String): Boolean {
        val refSet = componentResMap[type] ?: return false
        return refSet.contains(ref)
    }

    private fun writeFile(xmlFile: File, root: Element) {
        FileWriter(xmlFile).use {
            XMLWriter(it).apply {
                write(root)
            }
        }
    }

    private fun elementTraversal(root: Element, callback: (Element) -> Boolean) {
        if (!callback.invoke(root)) {
            return
        }
        root.elementIterator().forEach {
            elementTraversal(it, callback)
        }
    }

}
