package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.plugin.component.utils.InjectHelper
import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.TransformInvocation

/**
 * @author panxinghai
 *
 * date : 2019-09-09 18:06
 */
abstract class BaseTraversalTransform : BaseTransform() {
    override fun transform(transformInvocation: TransformInvocation?) {
        super.transform(transformInvocation)
        val inputs = transformInvocation?.inputs ?: return

        preTraversal(transformInvocation)
        inputs.forEach { input ->
            input.directoryInputs.forEach { dirInput ->
                InjectHelper.instance.appendClassPath(dirInput.file.absolutePath)
            }
            input.jarInputs.forEach {
                InjectHelper.instance.appendClassPath(it.file.absolutePath)
            }
        }

        preTransform(transformInvocation)
        inputs.forEach { input ->
            input.directoryInputs.forEach { dirInput ->
                if (!onDirVisited(dirInput, transformInvocation)) {
                    outputFiles(transformInvocation.outputProvider, dirInput)
                }
            }
            input.jarInputs.forEach {
                if (!onJarVisited(it, transformInvocation)) {
                    outputJarFile(transformInvocation.outputProvider, it)
                }
            }
        }
        postTransform(transformInvocation)
    }

    abstract fun onDirVisited(dirInput: DirectoryInput, transformInvocation: TransformInvocation): Boolean

    abstract fun onJarVisited(jarInput: JarInput, transformInvocation: TransformInvocation): Boolean

    protected open fun preTraversal(transformInvocation: TransformInvocation) {

    }
    protected open fun preTransform(transformInvocation: TransformInvocation) {

    }

    abstract fun postTransform(transformInvocation: TransformInvocation)
}