package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.component.Constants
import cn.soul.android.component.IRouterFactory
import cn.soul.android.component.annotation.Router
import cn.soul.android.plugin.component.utils.InjectHelper
import cn.soul.android.plugin.component.utils.Log
import com.android.build.api.transform.Format
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import javassist.CtClass
import javassist.CtMethod
import java.io.File

/**
 * @author panxinghai
 *
 * date : 2019-07-19 18:09
 */
class RouterCompileTransform : BaseTransform() {
    override fun transform(transformInvocation: TransformInvocation?) {
        super.transform(transformInvocation)
        val nodeList = mutableListOf<Pair<String, String>>()
        val inputs = transformInvocation?.inputs ?: return
        inputs.forEach { input ->
            input.directoryInputs.forEach { dirInput ->
                InjectHelper.instance.appendClassPath(dirInput.file.absolutePath)
                InjectHelper.instance.processFiles(dirInput.file)
                        .nameFilter { file -> file.name.endsWith(".class") }
                        .classFilter { ctClass ->
                            ctClass.getAnnotation(Router::class.java) != null
                        }.forEach {
                            println("router:${it.name}")
                            val routerAnnotation = it.getAnnotation(Router::class.java) as Router
                            val path = routerAnnotation.path
                            nodeList.add(Pair(path, it.name))
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
        if (nodeList.size > 0) {
            genRouterClass(dest, nodeList)
        }
    }

    private fun genRouterClass(dir: File, nodeList: List<Pair<String, String>>) {
        try {
            val classPool = InjectHelper.instance.getClassPool()
            val genClass = classPool.makeClass(Constants.GEN_FILE_PACKAGE_NAME + getRouterClassName())
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
            builder.append("instance.addRouterNode(\"${it.first}\", " +
                    "new ${Constants.ACTIVITY_NODE_CLASSNAME}(\"${it.first}\", ${it.second}.class));")
        }

        builder.append("}")
        return builder.toString()
    }

    private fun getRouterClassName(): String {
        return "SoulRouterNodeFactory"
    }

    override fun getName(): String {
        return "routerCompile"
    }
}