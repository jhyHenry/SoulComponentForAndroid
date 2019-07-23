package cn.soul.android.plugin.component.extesion

import java.io.File
import java.lang.IllegalArgumentException

/**
 * Created by nebula on 2019-07-21
 */
open class Dependencies {
    internal val dependenciesCollection = mutableListOf<File>()
    private val dependenciesPath = mutableListOf<String>()

    fun implementation(path: String) {
        dependenciesPath.add(path)
    }

    internal fun resolveDependencies(extension: ComponentExtension) {
        dependenciesPath.forEach {
            val strings = it.split(':')
            val size = strings.size
            if (size < 2 || size > 3) {
                throw IllegalArgumentException(
                        "wrong format: $it. implementation format must be \$componentName:\$version[:\$variantName]")
            }
            val name = strings[0]
            val version = strings[1]
            val variant = if (size == 3) {
                strings[2]
            } else {
                "release"
            }
            val path = "${extension.repoPath}/$name/$version/$variant"
            val file = File(path)
            if (!file.exists()) {
                throw IllegalArgumentException("can not resolve implementation:${file.absolutePath}")
            }
            dependenciesCollection.add(file)
        }
    }
}