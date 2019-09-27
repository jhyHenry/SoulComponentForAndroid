package cn.soul.android.plugin.component.utils

/**
 * @author panxinghai
 *
 * date : 2019-09-27 23:13
 */
class Descriptor {
    companion object {
        fun getTaskNameWithoutModule(name: String): String {
            return name.substring(name.lastIndexOf(':') + 1)
        }
    }
}