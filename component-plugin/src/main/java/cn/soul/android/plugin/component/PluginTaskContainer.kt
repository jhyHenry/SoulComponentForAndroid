package cn.soul.android.plugin.component

import cn.soul.android.plugin.component.tasks.AidlCompile
import cn.soul.android.plugin.component.tasks.CheckManifest
import com.android.build.gradle.internal.scope.TaskContainer
import com.android.build.gradle.internal.tasks.CheckManifest as RealCheckManifest


/**
 * @author panxinghai
 *
 * date : 2019-07-11 15:26
 */
class PluginTaskContainer(container: TaskContainer) : TaskContainer by container {
    var pluginCheckManifestTask: CheckManifest? = null
    var pluginAidlCompile: AidlCompile? = null
}