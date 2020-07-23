package cn.soul.android.plugin.component.resolve

import cn.soul.android.plugin.component.exception.RGenerateException
import cn.soul.android.plugin.component.utils.Log
import org.dom4j.Document
import org.dom4j.Element
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import java.io.File
import java.io.FileWriter
import java.util.*
import kotlin.collections.HashSet

/**
 * 为资源增加前缀的task。这里只能为打包为aar的module增加资源前缀，aar中只包含当前module中的代码，其依赖的lib
 * 没有打包到aar中，因此放在lib中的资源不会添加前缀，还是有可能会产生冲突
 *
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

    // 需要被处理的资源类型，这里枚举出来。styleable和attr没有增加前缀处理，是因为其本身的特殊性，推荐这里还是依赖代码规范处理
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

    // 有些资源类型 xml中和R中不一样，这里做转换
    private val attributeNameTypeMap = hashMapOf("string-array" to "array")

    private val reader: SAXReader = SAXReader()
    val componentResMap = hashMapOf<String, HashSet<String>>()
    var prefix = ""
    var symbolTableWithPackageName: File? = null

    // 第一遍遍历所有资源，确定需要处理的资源范围
    fun initWithPackagedRes(prefix: String, dir: File) {
        this.prefix = prefix
        componentResMap.clear()
        accessTypeSet.addAll(resTypeSet)
        require(dir.parent != "packaged_res") {
            "error dir, prefixHelper must receive packaged_res/\$variantName dir."
        }
        dir.walk().filter { it.isFile && !(it.name.startsWith("values") && it.name.endsWith(".xml")) }
                .forEach {
                    // obtain res type, split name because of Android resources dimens. eg:<resources_name>-<config_qualifier>
                    val type = it.parentFile.name.split('-')[0]
                    componentResMap.computeIfAbsent(type) {
                        hashSetOf()
                    }.add(it.name.split('.')[0])
                    it.renameTo(File(it.parentFile, prefix + it.name))
                    Log.d(it.parentFile.absolutePath + prefix + it.name)
                }
        val documents: Array<Document> = Array(2) { reader.read(File(dir, "values/values.xml")) }
        if (File(dir, "values-zh-rTW/values-zh-rTW.xml").exists()) {
            documents[documents.size - 1] = reader.read(File(dir, "values-zh-rTW/values-zh-rTW.xml"))
        }

        documents.forEach {
            val root = it.rootElement ?: return@forEach
            root.elementIterator().forEach {
                var type = it.name
                // handle special type. eg:[string-array]
                if (attributeNameTypeMap.containsKey(type)) {
                    type = attributeNameTypeMap[type]
                }
                if (!accessTypeSet.contains(type)) {
                    return@forEach
                }
                val attribute = it.attribute("name")
                componentResMap.computeIfAbsent(type) { hashSetOf() }.add(attribute.value)
            }
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
        if (!file.exists()) return
        val document = reader.read(file)
        val element = document.rootElement
        if (element.name != "resources") {
            Log.e("wrong values file: ${file.absolutePath}, skip prefix.")
            return
        }
        element.elementIterator().forEach {

            // 特殊类型转换 string-array array
            if (attributeNameTypeMap.containsKey(it.name)) {
                it.name = attributeNameTypeMap[it.name]
            }

            if (!accessTypeSet.contains(it.name)) {
                Log.d("wrong values attribute: ${it.name}, skip prefix.")
                return@forEach
            }

            val attribute = it.attribute("name")
            attribute.value = prefix + attribute.value
            Log.d("prefix values attribute: ${attribute.value}")
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

    fun checkRValid() {
        if (symbolTableWithPackageName == null) throw RGenerateException("symbolTableWithPackageName == null")
        // 文件生成失败抛异常
//        if (symbolTableWithPackageName!!.exists()) {
//            Log.d(symbolTableWithPackageName.toString())
//            throw RGenerateException(symbolTableWithPackageName.toString())
//        }

        // 文件生成失败抛异常
        val content = symbolTableWithPackageName!!.readText().split("\n")
        componentResMap.forEach { out ->
            out.value.forEach {
                val attr = "${out.key} ${prefix}${it}".replace(".", "_")
                Log.d(attr)
                if (!content.contains(attr)) throw RGenerateException(attr)
            }
        }
    }

}
