package cn.soul.android.plugin.component.tasks

import cn.soul.android.plugin.component.PluginVariantScope
import cn.soul.android.plugin.component.utils.Log
import com.android.build.gradle.internal.scope.TaskConfigAction
import org.gradle.api.tasks.Copy
import java.io.File

/**
 * Created by nebula on 2019-07-21
 * 目前只支持本地文件夹的仓库
 */
open class UploadComponent : Copy() {
    class ConfigAction(private val componentName: String,
                       private val versionName: String,
                       private val scope: PluginVariantScope) : TaskConfigAction<UploadComponent> {
        override fun getName(): String {
            return scope.getTaskName("upload")
        }

        override fun getType(): Class<UploadComponent> {
            return UploadComponent::class.java
        }

        override fun execute(task: UploadComponent) {
            task.destinationDir =
                    File("${scope.getComponentExtension().repoPath!!}/$componentName/$versionName/${scope.getFullName()}")
            task.from(scope.getAarLocation())
        }
    }
}