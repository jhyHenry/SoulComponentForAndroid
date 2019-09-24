package cn.soul.android.plugin.component.extesion

import cn.soul.android.plugin.component.utils.Log
import org.gradle.api.Action
import org.gradle.api.Project

/**
 * @author panxinghai
 *
 * date : 2019-07-12 12:00
 */
open class ComponentExtension {
    internal val dependencies = Dependencies()
    var componentName: String? = null
    var uploadPath: String? = null
    var repoPath: String? = null
    var logLevel: Log.Level? = null

    var resourcePrefix: String? = null

    fun dependencies(action: Action<Dependencies>) {
        action.execute(dependencies)
    }

    fun ensureComponentExtension(project: Project) {
        if (componentName == null) {
            componentName = project.name
        }
        if (uploadPath == null) {
            uploadPath = project.parent?.buildDir?.absolutePath
            if (uploadPath == null) {
                throw RuntimeException("got default build path error, please do not apply SoulComponent plugin in root project")
            }
        }
        if (repoPath == null) {
            repoPath = uploadPath
        }
        if (resourcePrefix == null) {
            resourcePrefix = "${project.name}_"
        }
    }
}