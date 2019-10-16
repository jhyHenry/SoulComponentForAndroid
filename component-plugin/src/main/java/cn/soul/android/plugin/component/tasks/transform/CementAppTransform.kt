package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.component.Constants
import cn.soul.android.component.combine.ITaskCollector
import cn.soul.android.component.combine.InitTask
import cn.soul.android.plugin.component.extesion.ComponentExtension
import cn.soul.android.plugin.component.manager.BuildType
import cn.soul.android.plugin.component.resolve.ZipHelper
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
import org.gradle.api.Project
import java.io.File
import java.lang.reflect.Modifier

/**
 * @author panxinghai
 *
 * date : 2019-10-14 22:35
 */
class CementAppTransform(private val project: Project) : TypeTraversalTransform() {
    private val mTaskNameList = arrayListOf<String>()
    private val mTaskNameListProvider: (CtClass) -> Unit = {
        if (!Modifier.isAbstract(it.modifiers) && !it.name.startsWith("cn.soul.android.component")) {
            mTaskNameList.add(it.name)
        }
    }

    private lateinit var mInitTaskCtClass: CtClass
    private val mComponentTaskProviderList = arrayListOf<String>()

    override fun preTransform(transformInvocation: TransformInvocation) {
        InjectHelper.instance.appendClassPath(ClassClassPath(InitTask::class.java))
        mInitTaskCtClass = InjectHelper.instance.getClassPool()[InitTask::class.java.name]
    }

    override fun onDirVisited(dirInput: DirectoryInput, transformInvocation: TransformInvocation): Boolean {
        if (buildType == BuildType.COMPONENT) {
            InjectHelper.instance.processFiles(dirInput.file)
                    .nameFilter { file -> file.name.endsWith(".class") }
                    .classFilter { ctClass ->
                        ctClass.subtypeOf(mInitTaskCtClass)
                    }.forEach {
                        mTaskNameListProvider.invoke(it)
                    }
            return false
        }
        InjectHelper.instance.processFiles(dirInput.file)
                .nameFilter { file -> file.name.endsWith(".class") }
                .classFilter { ctClass ->
                    ctClass.subtypeOf(mInitTaskCtClass)
                }.forEach {
                    mTaskNameListProvider.invoke(it)
                }
        return false
    }

    override fun onJarVisited(jarInput: JarInput, transformInvocation: TransformInvocation): Boolean {
        if (buildType == BuildType.COMPONENT) {
            return false
        }
        ZipHelper.traversalZip(jarInput.file) {
            if (it.name.startsWith(Constants.INIT_TASK_GEN_FILE_FOLDER)) {
                mComponentTaskProviderList.add(getNameWithEntryName(it.name))
            }
        }
        return false
    }

    override fun postTransform(transformInvocation: TransformInvocation) {
        val dest = transformInvocation.outputProvider.getContentLocation(
                "injectGen",
                TransformManager.CONTENT_CLASS,
                TransformManager.SCOPE_FULL_PROJECT,
                Format.DIRECTORY)
        if (buildType == BuildType.COMPONENT) {
            if (mTaskNameList.isNotEmpty()) {
                genComponentTaskProviderImpl(dest)
            }
            return
        }
        if (mTaskNameList.isNotEmpty() || mComponentTaskProviderList.isNotEmpty()) {
            genTaskCollectorImpl(dest)
        }
    }

    private fun genComponentTaskProviderImpl(dir: File) {
        try {
            val classPool = InjectHelper.instance.getClassPool()
            val name = Constants.INIT_TASK_GEN_FILE_PACKAGE + genComponentTaskProviderClassName()
            var genClass: CtClass? = classPool.getOrNull(name)
            if (genClass == null) {
                genClass = classPool.makeClass(name)
                genClass.addInterface(classPool.get(ITaskCollector::class.java.name))
                genClass.addMethod(genGatherComponentTasksMethod(genClass))
            } else {
                if (genClass.isFrozen) {
                    genClass.defrost()
                }
                genClass.getDeclaredMethod("gatherComponentTasks")
                        .setBody(genGatherComponentTaskMethodBody())
            }
            genClass?.writeFile(dir.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            if (e.message != null) {
                Log.e(e.message!!)
            }
        }
    }


    private fun genGatherComponentTasksMethod(genClass: CtClass): CtMethod {
        return CtMethod.make(gatherComponentTasksSrc(), genClass)
    }

    private fun gatherComponentTasksSrc(): String {
        return "public java.util.List gatherComponentTasks()" +
                genGatherComponentTaskMethodBody()
    }

    private fun genGatherComponentTaskMethodBody(): String {
        val sb = StringBuilder("{java.util.ArrayList list = new java.util.ArrayList();")
        mTaskNameList.forEach {
            sb.append("list.add(new $it());")
        }
        sb.append("return list;}")
        return sb.toString()
    }

    private fun genTaskCollectorImpl(dir: File) {
        try {
            val classPool = InjectHelper.instance.getClassPool()
            val name = Constants.INIT_TASK_GEN_FILE_PACKAGE + Constants.INIT_TASK_COLLECTOR_IMPL_NAME
            var genClass: CtClass? = classPool.getOrNull(name)
            if (genClass == null) {
                genClass = classPool.makeClass(name)
                genClass.addInterface(classPool.get(ITaskCollector::class.java.name))
                genClass.addMethod(genGatherTasksMethod(genClass))
            } else {
                if (genClass.isFrozen) {
                    genClass.defrost()
                }
                genClass.getDeclaredMethod("gatherTasks")
                        .setBody(genGatherTaskMethodBody())
            }
            genClass?.writeFile(dir.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            if (e.message != null) {
                Log.e(e.message!!)
            }
        }
    }

    private fun genGatherTasksMethod(genClass: CtClass): CtMethod {
        return CtMethod.make(gatherTasksSrc(), genClass)
    }

    private fun gatherTasksSrc(): String {
        return "public java.util.List gatherTasks()" +
                genGatherTaskMethodBody()
    }

    private fun genGatherTaskMethodBody(): String {
        val sb = StringBuilder("{java.util.ArrayList list = new java.util.ArrayList();")
        mTaskNameList.forEach {
            sb.append("list.add(new $it());")
        }
        mComponentTaskProviderList.forEach {
            sb.append("list.addAll(new ${Constants.INIT_TASK_GEN_FILE_PACKAGE}$it().gatherComponentTasks());")
        }
        sb.append("return list;}")
        return sb.toString()
    }

    private fun getNameWithEntryName(name: String): String {
        return name.split('/').last().split('.')[0]
    }

    private fun genComponentTaskProviderClassName(): String {
        val componentName = getComponentExtension().componentName
        return "\$$componentName\$TaskProvider"
    }

    private fun getComponentExtension(): ComponentExtension {
        return project.extensions.findByType(ComponentExtension::class.java)
                ?: throw RuntimeException("can not find ComponentExtension, please check your build.gradle file")
    }

    override fun getName(): String {
        return "cementApp"
    }
}