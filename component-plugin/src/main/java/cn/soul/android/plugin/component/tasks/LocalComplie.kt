package cn.soul.android.plugin.component.tasks

import cn.soul.android.plugin.component.utils.Log
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

/**
 * Author   : walid
 * Date     : 2020-07-21  19:53
 * Describe :
 */

open class LocalCompile : DefaultTask() {

    @TaskAction
    fun taskAction() {
        Log.d("LocalCompile:${project.name}")
    }

    class ConfigAction(val project: Project)
        : TaskCreationAction<CommonLocalComponent>() {
        override val name: String
            get() = "localCompile"
        override val type: Class<CommonLocalComponent>
            get() = CommonLocalComponent::class.java

        override fun configure(task: CommonLocalComponent) {

        }
    }

}