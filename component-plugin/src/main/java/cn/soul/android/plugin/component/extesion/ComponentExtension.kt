package cn.soul.android.plugin.component.extesion

import cn.soul.android.plugin.component.PluginVariantScope
import org.gradle.api.Project

/**
 * @author panxinghai
 *
 * date : 2019-07-12 12:00
 */
open class ComponentExtension {
    var archiveName: String? = null

    fun ensureComponentExtension(project: Project) {
        if (archiveName == null) {
            archiveName = project.name
        }
    }
}