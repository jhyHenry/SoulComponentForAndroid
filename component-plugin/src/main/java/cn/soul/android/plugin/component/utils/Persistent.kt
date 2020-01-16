package cn.soul.android.plugin.component.utils

/**
 * @author panxinghai
 *
 * date : 2020-01-16 12:25
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Persistent(val fileName: String = "") {
}