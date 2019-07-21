package cn.soul.android.plugin.component.extesion

import org.gradle.api.Action
import org.gradle.api.Project
import java.lang.RuntimeException

/**
 * @author panxinghai
 *
 * date : 2019-07-12 12:00
 */
open class ComponentExtension {
    internal val dependencies = Dependencies()
    var archiveName: String? = null
    var repoPath: String? = null
    var resourcePrefix: String? = null

    fun dependencies(action: Action<Dependencies>) {
        action.execute(dependencies)
    }

    fun ensureComponentExtension(project: Project) {
        if (archiveName == null) {
            archiveName = project.name
        }
        if (repoPath == null) {
            repoPath = project.parent?.buildDir?.absolutePath
            if (repoPath == null) {
                throw RuntimeException("got default build path error, please do not apply plugin in root project")
            }
        }
    }
}