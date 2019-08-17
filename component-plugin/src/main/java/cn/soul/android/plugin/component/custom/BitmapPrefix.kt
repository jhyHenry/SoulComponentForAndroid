package cn.soul.android.plugin.component.custom

/**
 * Created by nebula on 2019-08-17
 */
class BitmapPrefix : CommonPrefix() {
    override fun targetAttrNameListWithDefaultNamespace(): List<String> {
        return listOf("src")
    }

    override fun elementName(): String {
        return "bitmap"
    }
}