package cn.soul.android.plugin.component.tasks

import cn.soul.android.plugin.component.PluginVariantScope
import cn.soul.android.plugin.component.utils.Log
import com.android.build.gradle.internal.scope.TaskConfigAction
import org.gradle.api.tasks.Copy
import java.io.File

/**
 * Created by nebula on 2019-07-21
 */
open class UploadComponent : Copy() {
    class ConfigAction(private val componentName: String,
                       private val scope: PluginVariantScope) : TaskConfigAction<UploadComponent> {
        override fun getName(): String {
            return scope.getTaskName("upload", "Component")
        }

        override fun getType(): Class<UploadComponent> {
            return UploadComponent::class.java
        }

        override fun execute(task: UploadComponent) {
            task.destinationDir = File("${scope.getComponentExtension().repoPath!!}/$componentName")
            task.from(scope.getAarLocation())
        }
    }
}