package cn.soul.android.plugin.component.custom

import cn.soul.android.plugin.component.utils.AndroidXmlHelper
import org.dom4j.QName

/**
 * Created by nebula on 2019-08-17
 */
abstract class CommonPrefix : IElementPrefix {
    override fun subElementPath(): String {
        return ""
    }

    override fun targetAttrQNameList(): List<QName> {
        val result = mutableListOf<QName>()
        targetAttrNameListWithDefaultNamespace().forEach {
            result.add(QName.get(it, defaultNamespace()))
        }
        result.addAll(targetAttrNameListWithCustomNamespace())
        return result
    }

    abstract fun targetAttrNameListWithDefaultNamespace(): List<String>

    fun targetAttrNameListWithCustomNamespace(): List<QName> {
        return emptyList()
    }

    fun defaultNamespace(): String {
        return AndroidXmlHelper.ANDROID_URL
    }

    override fun prefix(resourceType: String, text: String, prefix: String): String {
        return "$resourceType$prefix$text"
    }
}