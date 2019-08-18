package cn.soul.android.plugin.component.custom

/**
 * Created by nebula on 2019-08-18
 */
class SimplePrefix(private val elementName: String,
                   private val attrNameList: List<String>) : CommonPrefix() {

    override fun elementName(): String {
        return elementName
    }

    override fun targetAttrNameListWithDefaultNamespace(): List<String> {
        return attrNameList
    }
}