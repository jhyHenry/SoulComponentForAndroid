package cn.soul.android.plugin.component

import cn.soul.android.plugin.component.tasks.AidlCompile
import cn.soul.android.plugin.component.tasks.CheckManifest
import cn.soul.android.plugin.component.tasks.MergeResources
import com.android.build.gradle.internal.scope.TaskContainer
import org.gradle.api.Task


/**
 * @author panxinghai
 *
 * date : 2019-07-11 15:26
 */
class PluginTaskContainer(container: TaskContainer) : TaskContainer by container {
    var pluginCheckManifestTask: CheckManifest? = null
    var pluginAidlCompile: AidlCompile? = null
    var pluginMergeResourcesTask: MergeResources? = null


    //anchor task
    var resourceGenTask: Task? = null
    var assetGenTask: Task? = null

}