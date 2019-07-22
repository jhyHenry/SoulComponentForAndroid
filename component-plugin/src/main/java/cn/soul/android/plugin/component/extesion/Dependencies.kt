package cn.soul.android.plugin.component.extesion

import java.io.File
import java.lang.IllegalArgumentException

/**
 * Created by nebula on 2019-07-21
 */
open class Dependencies() {
    internal val dependenciesCollection = mutableListOf<File>()
    private val dependenciesPath = mutableListOf<String>()

    fun implementation(path: String) {
        dependenciesPath.add(path)
    }

    internal fun resolveDependencies(extension: ComponentExtension) {
        dependenciesPath.forEach {
            val index = it.indexOf(':')
            if (index == -1) {
                throw IllegalArgumentException(
                        "wrong format: $it. implementation format must be \$componentName:\$version")
            }
            val name = it.substring(0, index)
            val version = it.substring(index + 1)
            val path = "${extension.repoPath}/$name/$version"
            val file = File(path)
            if (!file.exists()) {
                throw IllegalArgumentException("can not resolve implementation:${file.absolutePath}")
            }
            dependenciesCollection.add(file)
        }
    }
}