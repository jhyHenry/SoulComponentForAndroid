package cn.soul.android.plugin.component.utils

import cn.soul.android.plugin.component.extesion.ComponentExtension
import com.android.build.api.transform.Format
import com.android.build.api.transform.TransformInvocation
import org.gradle.api.Project
import java.io.File

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

fun TransformInvocation.persistenceOutputDir(): File {
    inputs.forEach {
        it.jarInputs.forEach { jarInput ->
            return outputProvider.getContentLocation(jarInput.name,
                    jarInput.contentTypes,
                    jarInput.scopes, Format.JAR).parentFile
        }
        it.directoryInputs.forEach { dirInput ->
            return outputProvider.getContentLocation(dirInput.name,
                    dirInput.contentTypes,
                    dirInput.scopes, Format.DIRECTORY).parentFile
        }
    }
    throw RuntimeException("create persistence output directory error, no input of transformInvocation detected.")
}