package cn.soul.android.plugin.component.utils

import cn.soul.android.plugin.component.extesion.ComponentExtension
import org.gradle.api.Project

/**
 * Created by nebula on 2019-12-25
 */
var componentExtension: ComponentExtension? = null

fun Project.componentExtension(): ComponentExtension {
    if (componentExtension == null) {
        componentExtension = extensions.getByType(ComponentExtension::class.java)
    }
    return componentExtension!!
}