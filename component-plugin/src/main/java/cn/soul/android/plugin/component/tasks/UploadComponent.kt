package cn.soul.android.plugin.component.tasks

import cn.soul.android.plugin.component.PluginVariantScope
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.tasks.AndroidVariantTask

/**
 * Created by nebula on 2019-07-21
 */
open class UploadComponent : AndroidVariantTask() {

    class ConfigAction(val scope: PluginVariantScope) : TaskConfigAction<UploadComponent> {
        override fun getName(): String {
            return scope.getTaskName("upload", "componentArchives")
        }

        override fun getType(): Class<UploadComponent> {
            return UploadComponent::class.java
        }

        override fun execute(task: UploadComponent) {

        }
    }
}