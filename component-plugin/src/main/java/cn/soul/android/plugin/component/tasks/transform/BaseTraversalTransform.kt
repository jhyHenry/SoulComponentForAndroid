package cn.soul.android.plugin.component.tasks.transform

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

        preTransform(transformInvocation)
        inputs.forEach { input ->
            input.directoryInputs.forEach { dirInput ->
                onDirVisited(dirInput)
                outputFiles(transformInvocation.outputProvider, dirInput)
            }
            input.jarInputs.forEach {
                onJarVisited(it)
                outputJarFile(transformInvocation.outputProvider, it)
            }
        }
        postTransform(transformInvocation)
    }

    abstract fun onDirVisited(dirInput: DirectoryInput)

    abstract fun onJarVisited(jarInput: JarInput)

    fun preTransform(transformInvocation: TransformInvocation) {

    }

    abstract fun postTransform(transformInvocation: TransformInvocation)
}