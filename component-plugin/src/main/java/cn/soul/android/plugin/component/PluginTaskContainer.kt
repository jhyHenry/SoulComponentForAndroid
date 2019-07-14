package cn.soul.android.plugin.component

import cn.soul.android.plugin.component.tasks.AidlCompile
import cn.soul.android.plugin.component.tasks.BundleAar
import cn.soul.android.plugin.component.tasks.CheckManifest
import cn.soul.android.plugin.component.tasks.MergeResources
import com.android.build.gradle.internal.scope.TaskContainer
import org.gradle.api.Task
import org.gradle.api.tasks.compile.JavaCompile


/**
 * @author panxinghai
 *
 * date : 2019-07-11 15:26
 */
class PluginTaskContainer(container: TaskContainer) : TaskContainer by container {
    var pluginCheckManifestTask: CheckManifest? = null
    var pluginJavacTask: JavaCompile? = null
    var pluginAidlCompile: AidlCompile? = null
    var pluginMergeResourcesTask: MergeResources? = null
    var pluginBundleAarTask: BundleAar? = null

    //anchor task
    var resourceGenTask: Task? = null
    var assetGenTask: Task? = null
}