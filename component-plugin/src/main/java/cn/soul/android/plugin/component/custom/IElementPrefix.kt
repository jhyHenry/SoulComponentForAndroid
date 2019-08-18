package cn.soul.android.plugin.component.custom

import org.dom4j.QName

/**
 * Created by nebula on 2019-08-17
 */
interface IElementPrefix {
    fun elementName(): String

    fun targetAttrQNameList(): List<QName>

    fun childElementPrefixes(): List<IElementPrefix>

    fun prefix(resourceType: String, text: String, prefix: String): String
}