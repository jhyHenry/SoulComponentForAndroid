package cn.soul.android.plugin.component.custom

import cn.soul.android.plugin.component.utils.AndroidXmlHelper
import cn.soul.android.plugin.component.utils.Log
import org.dom4j.QName

/**
 * Created by nebula on 2019-08-17
 */
abstract class CommonPrefix : IElementPrefix {
    override fun targetAttrQNameList(): List<QName> {
        val result = mutableListOf<QName>()
        targetAttrNameListWithDefaultNamespace().forEach {
            result.add(QName.get(it, defaultNamespace()))
        }
        result.addAll(targetAttrNameListWithCustomNamespace())
        return result
    }

    override fun childElementPrefixes(): List<IElementPrefix> {
        return emptyList()
    }

    protected fun targetAttrNameListWithCustomNamespace(): List<QName> {
        return emptyList()
    }

    protected fun defaultNamespace(): String {
        return AndroidXmlHelper.ANDROID_URL
    }

    override fun prefix(resourceType: String, text: String, prefix: String): String {
        return "$resourceType$prefix$text"
    }

    abstract override fun elementName(): String

    abstract fun targetAttrNameListWithDefaultNamespace(): List<String>
}