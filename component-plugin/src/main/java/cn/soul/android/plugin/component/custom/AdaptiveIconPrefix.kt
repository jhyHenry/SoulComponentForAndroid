package cn.soul.android.plugin.component.custom

/**
 * Created by nebula on 2019-08-17
 */
class AdaptiveIconPrefix : CommonPrefix() {

    override fun elementName(): String {
        return "adaptive-icon"
    }

    override fun targetAttrNameListWithDefaultNamespace(): List<String> {
        return emptyList()
    }

    override fun childElementPrefixes(): List<IElementPrefix> {
        val list = ArrayList<IElementPrefix>()
        list.add(SimplePrefix("background", listOf("drawable")))
        list.add(SimplePrefix("foreground", listOf("drawable")))
        return list
    }
}