package cn.soul.android.plugin.component

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import org.gradle.api.Task
import java.io.File

/**
 * Created by nebula on 2019-07-15
 */
object PluginArtifactsHolder {
    private val artifactsMap = mutableMapOf<InternalArtifactType, File>()

    fun appendArtifact(scope: PluginVariantScope, type: InternalArtifactType, task: Task, fileName: String): File {
        return artifactsMap.computeIfAbsent(type) {
            scope.getInternalArtifactTypeOutputFile(
                    it,
                    task,
                    fileName)
        }
    }

    fun getArtifactFile(type: InternalArtifactType): File? {
        return artifactsMap[type]
    }

}