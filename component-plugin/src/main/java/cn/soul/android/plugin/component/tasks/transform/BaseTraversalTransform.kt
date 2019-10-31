package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.plugin.component.utils.InjectHelper
import cn.soul.android.plugin.component.utils.Log
import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.TransformInvocation

/**
 * @author panxinghai
 *
 * date : 2019-09-09 18:06
 */
abstract class BaseTraversalTransform : BaseTransform() {
    private var timeCost = 0L
    override fun transform(transformInvocation: TransformInvocation?) {
        super.transform(transformInvocation)
        val current = System.currentTimeMillis()
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
        timeCost = System.currentTimeMillis() - current
        Log.p("transform time cost: ${timeCost}ms")
    }

    /**
     * @param dirInput directory of transform input
     * @param transformInvocation transformInvocation of transform
     * @return if return false , sub class will not consume file input, [BaseTraversalTransform]
     * will write result to target directory for next transform. Otherwise [BaseTraversalTransform]
     * will ignore build result.
     */
    abstract fun onDirVisited(dirInput: DirectoryInput, transformInvocation: TransformInvocation): Boolean

    /**
     * see [onDirVisited]
     */
    abstract fun onJarVisited(jarInput: JarInput, transformInvocation: TransformInvocation): Boolean

    /**
     * [BaseTraversalTransform] will traversal first to load all input files to Javassist ClassPool,
     * this method run before the traversal.
     */
    protected open fun preTraversal(transformInvocation: TransformInvocation) {

    }

    /**
     * called before all [onDirVisited] and [onJarVisited]
     */
    protected open fun preTransform(transformInvocation: TransformInvocation) {

    }

    /**
     * called after all [onDirVisited] and [onJarVisited]
     */
    abstract fun postTransform(transformInvocation: TransformInvocation)
}