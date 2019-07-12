package cn.soul.android.plugin.component

import com.android.build.gradle.internal.TaskFactory
import com.android.build.gradle.internal.scope.TaskConfigAction
import org.gradle.api.Task

/**
 * @author panxinghai
 *
 * date : 2019-07-12 17:38
 */
class PluginTaskFactory(private val factory: TaskFactory) : TaskFactory by factory {
    override fun <T : Task?> create(configAction: TaskConfigAction<T>?): T {
        val task = factory.create(configAction)
        task?.group = "component"
        return task
    }

    override fun create(name: String?): Task {
        val task = factory.create(name)
        task.group = "component"
        return task
    }
}