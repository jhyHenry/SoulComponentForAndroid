package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.component.combine.InitTask
import cn.soul.android.component.combine.InitTaskManager
import cn.soul.android.plugin.component.manager.BuildType
import cn.soul.android.plugin.component.resolve.ZipHelper
import cn.soul.android.plugin.component.utils.InjectHelper
import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.TransformInvocation
import javassist.ClassClassPath
import javassist.CtClass
import java.lang.reflect.Modifier

/**
 * @author panxinghai
 *
 * date : 2019-10-14 22:35
 */
class CementAppTransform : TypeTraversalTransform() {
    private val mTaskNameList = arrayListOf<String>()
    private val mClassPool = InjectHelper.instance.getClassPool()
    private val mTaskNameListProvider: (CtClass) -> Unit = {
        if (!Modifier.isAbstract(it.modifiers)) {
            mTaskNameList.add(it.name)
        }
    }

    private lateinit var mInitTaskCtClass: CtClass

    override fun preTransform(transformInvocation: TransformInvocation) {
        mClassPool.appendClassPath(ClassClassPath(InitTask::class.java))
        mInitTaskCtClass = mClassPool[InitTask::class.java.name]
    }

    override fun onDirVisited(dirInput: DirectoryInput, transformInvocation: TransformInvocation): Boolean {
        if (buildType == BuildType.COMPONENT) {
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
        }
        return false
    }

    override fun postTransform(transformInvocation: TransformInvocation) {
        if (mTaskNameList.isNotEmpty()) {
            val managerCtClass = mClassPool[InitTaskManager::class.java.name]
            val ctMethod = managerCtClass.getDeclaredMethod("gatherTasks")
            ctMethod.setBody(genGatherTaskMethodBody())
        }
    }

    private fun genGatherTaskMethodBody(): String {
        val sb = StringBuilder("{ArrayList list = new ArrayList();")
        mTaskNameList.forEach {
            sb.append("list.add(new $it());")
        }
        sb.append("return list;}")
        return sb.toString()
    }

    override fun getName(): String {
        return "cementApp"
    }
}