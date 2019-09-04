package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.component.Constants
import cn.soul.android.component.IRouterFactory
import cn.soul.android.component.annotation.Router
import cn.soul.android.plugin.component.extesion.ComponentExtension
import cn.soul.android.plugin.component.utils.InjectHelper
import cn.soul.android.plugin.component.utils.Log
import com.android.build.api.transform.Format
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import javassist.CtClass
import javassist.CtMethod
import org.gradle.api.Project
import java.io.File

/**
 * @author panxinghai
 *
 * date : 2019-07-19 18:09
 */
class RouterCompileTransform(private val project: Project) : BaseTransform() {
    override fun transform(transformInvocation: TransformInvocation?) {
        super.transform(transformInvocation)
        val nodeMapByGroup = mutableMapOf<String, List<Pair<String, String>>>()
        val inputs = transformInvocation?.inputs ?: return

        //traversal all .class file and find the class which annotate by Router, record router path
        //and Class for RouterNode construction
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
                                listOf(Pair(path, it.name))
                            }

                        }
                outputFiles(transformInvocation.outputProvider, dirInput)
            }
            input.jarInputs.forEach {
                InjectHelper.instance.appendClassPath(it.file.absolutePath)
                outputJarFile(transformInvocation.outputProvider, it)
            }
        }
        val dest = transformInvocation.outputProvider.getContentLocation(
                "routerGen",
                TransformManager.CONTENT_CLASS,
                TransformManager.PROJECT_ONLY,
                Format.DIRECTORY)
        if (nodeMapByGroup.isNotEmpty()) {
            nodeMapByGroup.forEach {
                genRouterClass(dest, it.key, it.value)
            }
        }
    }

    private fun genRouterClass(dir: File, group: String, nodeList: List<Pair<String, String>>) {
        try {
            val classPool = InjectHelper.instance.getClassPool()
            val genClass = classPool.makeClass(Constants.GEN_FILE_PACKAGE_NAME + genRouterClassName(group))
            genClass.addInterface(classPool.get(IRouterFactory::class.java.name))
            genClass.addMethod(genProduceNodesMethod(genClass, nodeList))
            genClass.writeFile(dir.absolutePath)
            genClass.detach()
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
        val builder = StringBuilder("public void produceRouterNodes(${Constants.SOUL_ROUTER_CLASSNAME} instance) {")
        nodeList.forEach {
            builder.append("instance.addRouterNode(new ${Constants.ACTIVITY_NODE_CLASSNAME}(\"${it.first}\", ${it.second}.class));")
        }

        builder.append("}")
        return builder.toString()
    }

    private fun genRouterClassName(group: String): String {
        val componentName = getComponentExtension().componentName
        return "${group}\$${componentName}\$NodeFactory"
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