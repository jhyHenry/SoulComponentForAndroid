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
        Log.d("debugComponent:" + project.rootProject.properties["debugComponent"].toString())

        val debugComponents = project.rootProject.properties["debugComponent"].toString().split(",")

        // 组件远程依赖
        interfaceApis.forEach {
            val compName = it.split(":")[1]
            val contains: Boolean = debugComponents.contains(compName)
            // 判断本地依赖
            if (contains) {
                project.dependencies.add("compileOnly", project.fileTree("${project.rootDir}/repo/build").include("*.jar"))
            } else {
                project.dependencies.add("compileOnly", "$it@jar")
            }

            if (addRuntimeDependencies) {
                if (contains) {
                    val fileExists = File("${project.rootDir}/${compName}/build/outputs/aar/${compName}-debug.aar").exists()
                    Log.d("${project.rootDir}/${compName}/build/outputs/aar/*-debug.aar fileExists${fileExists}")
//                    if (fileExists) {
                    project.dependencies.add("compile", project.fileTree("${project.rootDir}/${compName}/build/outputs/aar/").include("*-debug.aar"))
//                    } else {
//                        project.dependencies.add("implementation", project.project(":${compName}"))
//                    }
                } else {
                    project.dependencies.add("implementation", "$it@aar")
                }
            }
        }

        // 组件本地依赖
        localInterfaceApis.forEach {
            project.dependencies.add("compileOnly", project.fileTree("${project.rootDir}/repo/build").include("*.jar"))
            if (addRuntimeDependencies) {
                val arr = it.split(":")
                val compName: String
                compName = if (arr.size > 1) arr[1] else it
//                project.dependencies.add("implementation", project.(":${compName}"))
                project.dependencies.add("implementation", project.fileTree("${project.rootDir}/${compName}/build/outputs/aar/").include("*-debug.aar"))
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
