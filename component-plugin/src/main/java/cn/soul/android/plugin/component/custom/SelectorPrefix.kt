package cn.soul.android.plugin.component.custom

/**
 * Created by nebula on 2019-08-17
 */
class SelectorPrefix : CommonPrefix() {

    override fun elementName(): String {
        return "selector"
    }

    override fun targetAttrNameListWithDefaultNamespace(): List<String> {
        return emptyList()
    }

    override fun childElementPrefixes(): List<IElementPrefix> {
        val list = ArrayList<IElementPrefix>()
        list.add(SimplePrefix("item", listOf("color")))
        return list;
    }
}