package cn.soul.android.plugin.component.custom

/**
 * Created by nebula on 2019-08-17
 */
class SelectorPrefix : CommonPrefix() {

    override fun elementName(): String {
        return "selector"
    }

    override fun subElementPath(): String {
        return "item"
    }

    override fun targetAttrNameListWithDefaultNamespace(): List<String> {
        return listOf("color")
    }
}