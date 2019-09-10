package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.component.Constants
import cn.soul.android.component.annotation.Router
import cn.soul.android.component.template.IRouterFactory
import cn.soul.android.component.template.IRouterLazyLoader
import cn.soul.android.plugin.component.extesion.ComponentExtension
import cn.soul.android.plugin.component.utils.InjectHelper
import cn.soul.android.plugin.component.utils.Log
import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.Format
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import javassist.ClassClassPath
import javassist.CtClass
import javassist.CtMethod
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File
import java.util.zip.ZipFile

/**
 * @author panxinghai
 *
 * date : 2019-07-19 18:09
 */
class RouterCompileTransform(private val project: Project,
                             private val buildType: BuildType) : BaseTraversalTransform() {
    enum class BuildType {
        COMPONENT,
        APPLICATION,
    }

    private val nodeMapByGroup = mutableMapOf<String, ArrayList<Pair<String, String>>>()
    private val groupMap = mutableMapOf<String, ArrayList<String>>()

    override fun onDirVisited(dirInput: DirectoryInput) {
        //traversal all .class file and find the class which annotate by Router, record router path
        //and Class for RouterNode construction
        InjectHelper.instance.appendClassPath(dirInput.file.absolutePath)
        InjectHelper.instance.processFiles(dirInput.file)
                .nameFilter { file -> file.name.endsWith(".class") }
                .classFilter { ctClass ->
                    ctClass.getAnnotation(Router::class.java) != null
                }.forEach {
                    val routerAnnotation = it.getAnnotation(Router::class.java) as Router
                    val path = routerAnnotation.path
                    nodeMapByGroup.computeIfAbsent(getGroupWithPath(path, it.name)) {
                        arrayListOf()
                    }.add(Pair(path, it.name))
                }
    }

    override fun onJarVisited(jarInput: JarInput) {
        InjectHelper.instance.appendClassPath(jarInput.file.absolutePath)
        if (buildType == BuildType.APPLICATION) {
            val zip = ZipFile(jarInput.file)
            zip.use {
                it.entries().iterator().forEach { entry ->
                    if (entry.name.startsWith(Constants.GEN_FILE_PACKAGE_NAME_SPLIT_WITH_SLASH)) {
                        groupMap.computeIfAbsent(getGroupWithEntryName(entry.name)) {
                            arrayListOf()
                        }.add(getNameWithEntryName(entry.name))
                    }
                }
            }
        }
    }

    override fun postTransform(transformInvocation: TransformInvocation) {
        val dest = transformInvocation.outputProvider.getContentLocation(
                "routerGen",
                TransformManager.CONTENT_CLASS,
                TransformManager.PROJECT_ONLY,
                Format.DIRECTORY)

        if (nodeMapByGroup.isNotEmpty()) {
            nodeMapByGroup.forEach {
                genRouterFactoryImpl(dest, it.key, it.value)
            }
        }
        if (buildType == BuildType.APPLICATION) {
            nodeMapByGroup.forEach {
                groupMap.computeIfAbsent(it.key) {
                    arrayListOf()
                }.add(genRouterClassName(it.key))
            }
            InjectHelper.instance.getClassPool().appendClassPath(ClassClassPath(IRouterLazyLoader::class.java))
            InjectHelper.instance.getClassPool().appendClassPath(ClassClassPath(IRouterFactory::class.java))
            genRouterLazyLoaderImpl(dest, groupMap)
        }
    }

    private fun genRouterFactoryImpl(dir: File, group: String, nodeList: List<Pair<String, String>>) {
        try {
            val classPool = InjectHelper.instance.getClassPool()
            val name = Constants.GEN_FILE_PACKAGE_NAME + genRouterClassName(group)
            var genClass: CtClass? = classPool.getOrNull(name)
            if (genClass == null) {
                genClass = classPool.makeClass(name)
                genClass.addInterface(classPool.get(IRouterFactory::class.java.name))
                genClass.addMethod(genProduceNodesMethod(genClass, nodeList))
            } else {
                if (genClass.isFrozen) {
                    genClass.defrost()
                }
                genClass.getDeclaredMethod("produceRouterNodes")
                        .setBody(produceNodesMethodBodySrc(nodeList))
            }
            genClass?.writeFile(dir.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            if (e.message != null) {
                Log.e(e.message!!)
            }
        }
    }

    private fun genProduceNodesMethod(declaringClass: CtClass, nodeList: List<Pair<String, String>>): CtMethod {
        return CtMethod.make(produceNodesMethodSrc(nodeList), declaringClass)
    }

    private fun produceNodesMethodSrc(nodeList: List<Pair<String, String>>): String {
        return "public void produceRouterNodes(${Constants.SOUL_ROUTER_CLASSNAME} instance) " +
                produceNodesMethodBodySrc(nodeList)
    }

    private fun produceNodesMethodBodySrc(nodeList: List<Pair<String, String>>): String {
        val builder = StringBuilder("{")
        nodeList.forEach {
            builder.append("\$1.addRouterNode(new ${Constants.ACTIVITY_NODE_CLASSNAME}(\"${it.first}\", ${it.second}.class));")
        }
        builder.append("}")
        return builder.toString()
    }

    private fun genRouterLazyLoaderImpl(dir: File, groupMap: MutableMap<String, ArrayList<String>>) {
        try {
            val classPool = InjectHelper.instance.getClassPool()
            val genClass = classPool.makeClass(Constants.GEN_FILE_PACKAGE_NAME + "SoulRouterLazyLoaderImpl")
            genClass.addInterface(classPool.get(IRouterLazyLoader::class.java.name))
            genClass.addMethod(genLazyLoadFactoryByGroupMethod(groupMap, genClass))
            genClass.writeFile(dir.absolutePath)
            genClass.defrost()
        } catch (e: Exception) {
            e.printStackTrace()
            if (e.message != null) {
                Log.e(e.message!!)
            }
        }
    }

    private fun genLazyLoadFactoryByGroupMethod(groupMap: MutableMap<String, ArrayList<String>>, genClass: CtClass): CtMethod {
        return CtMethod.make(lazyLoadFactoryByGroupSrc(groupMap), genClass)
    }

    private fun lazyLoadFactoryByGroupSrc(groupMap: MutableMap<String, ArrayList<String>>): String {
        val sb = StringBuilder("public java.util.List lazyLoadFactoryByGroup(String arg) {")
                .append("java.util.ArrayList result =")
                .appendln("new java.util.ArrayList();")
        if (groupMap.isNotEmpty()) {
            sb.append("switch(\$1.hashCode()) {")
            groupMap.forEach {
                val group = it.key
                sb.append("case ${group.hashCode()}:{")
                it.value.forEach { name ->
                    sb.append("result.add(new ${Constants.GEN_FILE_PACKAGE_NAME}${name}());")
                }
                sb.append("return result;}")
            }
            sb.append("default:break;}")
        }
        sb.append("return result;}")
        Log.e(sb.toString())
        return sb.toString()
    }

    private fun genRouterClassName(group: String): String {
        val componentName = getComponentExtension().componentName
        return "$group\$$componentName\$NodeFactory"
    }

    private fun getComponentExtension(): ComponentExtension {
        return project.extensions.findByType(ComponentExtension::class.java)
                ?: throw RuntimeException("can not find ComponentExtension, please check your build.gradle file")
    }

    private fun getGroupWithPath(path: String, clazz: String): String {
        val strings = path.split('/')
        if (strings.size < 3) {
            throw GradleException("invalid router path: \"$path\" in $clazz. Router Path must starts with '/' and has a group segment")
        }
        return strings[1]
    }

    private fun getGroupWithEntryName(name: String): String {
        return name.split('/').last().split('$')[0]
    }

    private fun getNameWithEntryName(name: String): String {
        return name.split('/').last().split('.')[0]
    }

    override fun getName(): String {
        return "routerCompile"
    }
}