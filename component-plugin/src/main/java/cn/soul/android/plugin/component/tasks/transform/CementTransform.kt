package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.component.combine.InitTask
import cn.soul.android.plugin.component.utils.InjectHelper
import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.TransformInvocation

/**
 * @author panxinghai
 *
 * date : 2019-10-14 20:14
 */
class CementTransform : TypeTraversalTransform() {
    private val mTaskNameList = arrayListOf<String>()
    override fun onDirVisited(dirInput: DirectoryInput, transformInvocation: TransformInvocation): Boolean {
        val initTaskCtClass = InjectHelper.instance.getClassPool()[InitTask::class.java.name]
        InjectHelper.instance.processFiles(dirInput.file)
                .nameFilter { file -> file.name.endsWith(".class") }
                .classFilter { ctClass ->
                    ctClass.interfaces.contains(initTaskCtClass)
                }.forEach {
                    mTaskNameList.add(it.name)
                }
        return false
    }

    override fun onJarVisited(jarInput: JarInput, transformInvocation: TransformInvocation): Boolean {
        return false
    }

    override fun postTransform(transformInvocation: TransformInvocation) {
    }

    override fun getName(): String {
        return "cementCommon"
    }
}