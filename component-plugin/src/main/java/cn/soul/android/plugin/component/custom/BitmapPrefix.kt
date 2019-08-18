package cn.soul.android.plugin.component.custom

/**
 * Created by nebula on 2019-08-17
 */
class BitmapPrefix : CommonPrefix() {

    override fun elementName(): String {
        return "bitmap"
    }

    override fun targetAttrNameListWithDefaultNamespace(): List<String> {
        return listOf("src")
    }
}