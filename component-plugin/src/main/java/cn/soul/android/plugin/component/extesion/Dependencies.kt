package cn.soul.android.plugin.component.extesion

import cn.soul.android.plugin.component.utils.Log
import org.gradle.api.Project
import java.io.File

/**
 * Created by nebula on 2019-07-21
 */
open class Dependencies {

    private val dependenciesCollection = mutableListOf<File>()
    private val dependencies = mutableListOf<String>()
    private val interfaceApis = mutableListOf<String>()
    private val localInterfaceApis = mutableListOf<String>()

    fun implementation(path: String) {
        dependencies.add(path)
    }

    fun interfaceApi(mavenUrl: String) {
        interfaceApis.add(mavenUrl)
    }

    fun interfaceApi(mavenUrl: String, local: Boolean) {
        Log.d("local:${local}")
        if (local) {
            localInterfaceApis.add(mavenUrl)
        } else {
            interfaceApis.add(mavenUrl)
        }
    }

    internal fun appendDependencies(project: Project, addRuntimeDependencies: Boolean) {
        if (!addRuntimeDependencies) {
            return
        }
        dependencies.forEach {
            project.dependencies.add("implementation", it)
        }
    }

    internal fun appendInterfaceApis(project: Project, addRuntimeDependencies: Boolean) {
        Log.d("appendInterfaceApis$localInterfaceApis")
        // 组件远程依赖
        interfaceApis.forEach {
            Log.d("compileOnly:${it}")
            project.dependencies.add("compileOnly", "$it@jar")
            if (addRuntimeDependencies) {
                project.dependencies.add("implementation", "$it@aar")
            }
        }

        // 组件本地依赖
        localInterfaceApis.forEach {
            Log.d("compileOnly:${it}")
            project.dependencies.add("compileOnly", "$it@jar")
            if (addRuntimeDependencies) {
                project.dependencies.add("compile", project.project(":${it.split(":")[1]}"))
            }
        }
    }

//    private fun parse(parser: NotationConverter<String, Dependency>, value: String): Dependency {
//        return NotationParserBuilder.toType(Dependency::class.java).fromCharSequence(parser).toComposite().parseNotation(value)
//    }

    /**
     * resolve component dependencies, format like after:{name:version[:variant]}
     * @param extension extension created in plugin
     */
    internal fun resolveDependencies(extension: ComponentExtension) {
        dependencies.forEach {
            val strings = it.split(':')
            val size = strings.size
            require(!(size < 2 || size > 3)) {
                "wrong format: $it. implementation format must be \$componentName:\$version[:\$variantName]"
            }
            val name = strings[0]
            val version = strings[1]
            val variant = if (size == 3) {
                strings[2]
            } else {
                "release"
            }
            val path = "${extension.repoPath}/$name/$version/$variant/component.aar"
            val file = File(path)
            require(file.exists()) { "can not resolve implementation:${file.absolutePath}" }
            dependenciesCollection.add(file)
        }
    }
}