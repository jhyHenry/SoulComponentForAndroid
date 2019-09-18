package cn.soul.android.plugin.component.tasks

import cn.soul.android.plugin.component.PluginVariantScope
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import org.gradle.api.tasks.TaskAction

/**
 * Created by nebula on 2019-09-03
 */
class GenerateDependencies : AndroidVariantTask() {
    @TaskAction
    fun taskAction() {

    }

    class ConfigAction(private val scope: PluginVariantScope)
        : TaskConfigAction<GenerateDependencies> {
        override fun getName(): String {
            return scope.getTaskName("gen", "dependencies")
        }

        override fun getType(): Class<GenerateDependencies> {
            return GenerateDependencies::class.java
        }

        override fun execute(task: GenerateDependencies) {
            task.variantName = scope.fullVariantName
            scope.getVariantData().variantDependency.runtimeElements.allDependencies.forEach {
                it.group
            }
        }
    }
}