package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.component.Constants
import cn.soul.android.component.annotation.Router
import cn.soul.android.component.template.IRouterFactory
import cn.soul.android.component.template.IRouterLazyLoader
import cn.soul.android.plugin.component.extesion.ComponentExtension
import cn.soul.android.plugin.component.utils.InjectHelper
import cn.soul.android.plugin.component.utils.Log
import com.android.build.api.transform.Format
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import javassist.ClassClassPath
import javassist.CtClass
import javassist.CtMethod
import org.gradle.api.Project
import java.io.File

/**
 * @author panxinghai
 *
 * date : 2019-07-19 18:09
 */
class RouterCompileTransform(private val project: Project,
                             private val buildType: BuildType) : BaseTransform() {
    enum class BuildType {
        COMPONENT,
        APPLICATION,
    }

    override fun transform(transformInvocation: TransformInvocation?) {
        super.transform(transformInvocation)
        val inputs = transformInvocation?.inputs ?: return


        val dest = transformInvocation.outputProvider.getContentLocation(
                "routerGen",
                TransformManager.CONTENT_CLASS,
                TransformManager.PROJECT_ONLY,
                Format.DIRECTORY)
        if (buildType == BuildType.COMPONENT) {
            //traversal all .class file and find the class which annotate by Router, record router path
            //and Class for RouterNode construction
            val nodeMapByGroup = mutableMapOf<String, ArrayList<Pair<String, String>>>()
            inputs.forEach { input ->
                input.directoryInputs.forEach { dirInput ->
                    InjectHelper.instance.appendClassPath(dirInput.file.absolutePath)
                    InjectHelper.instance.processFiles(dirInput.file)
                            .nameFilter { file -> file.name.endsWith(".class") }
                            .classFilter { ctClass ->
                                ctClass.getAnnotation(Router::class.java) != null
                            }.forEach {
                                val routerAnnotation = it.getAnnotation(Router::class.java) as Router
                                val path = routerAnnotation.path
                                nodeMapByGroup.computeIfAbsent(getGroupWithPath(path)) { _ ->
                                    arrayListOf()
                                }.add(Pair(path, it.name))
                            }
                    outputFiles(transformInvocation.outputProvider, dirInput)
                }
                input.jarInputs.forEach {
                    InjectHelper.instance.appendClassPath(it.file.absolutePath)
                    outputJarFile(transformInvocation.outputProvider, it)
                }
            }
            if (nodeMapByGroup.isNotEmpty()) {
                nodeMapByGroup.forEach {
                    genRouterFactoryImpl(dest, it.key, it.value)
                }
            }
        } else {
            InjectHelper.instance.getClassPool().appendClassPath(ClassClassPath(IRouterLazyLoader::class.java))
            InjectHelper.instance.getClassPool().appendClassPath(ClassClassPath(IRouterFactory::class.java))
            genRouterLazyLoaderImpl(dest)
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
            genClass?.detach()
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

    private fun genRouterLazyLoaderImpl(dir: File) {
        try {
            val classPool = InjectHelper.instance.getClassPool()
            val genClass = classPool.makeClass(Constants.GEN_FILE_PACKAGE_NAME + "SoulRouterLazyLoaderImpl")
            genClass.addInterface(classPool.get(IRouterLazyLoader::class.java.name))
            genClass.addMethod(genLazyLoadFactoryByGroupMethod(dir, genClass))
            genClass.writeFile(dir.absolutePath)
            genClass.defrost()
            genClass.detach()
        } catch (e: Exception) {
            e.printStackTrace()
            if (e.message != null) {
                Log.e(e.message!!)
            }
        }
    }

    private fun genLazyLoadFactoryByGroupMethod(dir: File, genClass: CtClass): CtMethod {
        return CtMethod.make(lazyLoadFactoryByGroupSrc(dir), genClass)
    }

    private fun lazyLoadFactoryByGroupSrc(dir: File): String {
        var preGroup = ""
        val sb = StringBuilder("public java.util.List<${Constants.ROUTER_FACTORY_CLASSNAME}> lazyLoadFactoryByGroup(String arg) {")
        sb.append("switch(\$1) {")
        dir.walk().filter { it.isFile }
                .forEach {
                    val group = it.name.split('$')[0]
                    if (group != preGroup) {
                        if (group != "") {
                            sb.append("return result;}")
                        }
                        preGroup = group
                        sb.append("case \"$group\":{")
                                .append("java.util.ArrayList<${Constants.ROUTER_FACTORY_CLASSNAME}> result =")
                                .append("new java.util.ArrayList<>();")
                    }
                    sb.append("result.add(new ${Constants.GEN_FILE_PACKAGE_NAME}${it.name}());")
                }
        sb.append("}")
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

    private fun getGroupWithPath(path: String): String {
        return path.split('/')[1]
    }

    override fun getName(): String {
        return "routerCompile"
    }
}