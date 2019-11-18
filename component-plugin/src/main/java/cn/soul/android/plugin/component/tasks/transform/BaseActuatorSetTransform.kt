package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.plugin.component.resolve.ZipHelper
import cn.soul.android.plugin.component.utils.InjectHelper
import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.TransformInvocation

/**
 * @author panxinghai
 *
 * date : 2019-11-18 18:09
 */
abstract class BaseActuatorSetTransform : BaseTraversalTransform() {
    private var actuatorSet: Set<TransformActuator> = emptySet()

    override fun preTraversal(transformInvocation: TransformInvocation) {
        actuatorSet = getTransformActuatorSet()
        actuatorSet.forEach {
            it.preTraversal(transformInvocation)
        }
    }

    override fun onDirVisited(dirInput: DirectoryInput, transformInvocation: TransformInvocation): Boolean {
        InjectHelper.instance.processFiles(dirInput.file)
                .nameFilter { file -> file.name.endsWith(".class") }
                .forEach { ctClass ->
                    var modify = false
                    actuatorSet.forEach {
                        modify = modify or it.onClassVisited(ctClass, transformInvocation)
                    }
                    if (modify) {
                        ctClass.writeFile(dirInput.file.absolutePath)
                    }
                }
        return false
    }

    override fun onJarVisited(jarInput: JarInput, transformInvocation: TransformInvocation): Boolean {
        ZipHelper.traversalZip(jarInput.file) { entry ->
            actuatorSet.forEach {
                it.onJarEntryVisited(entry, transformInvocation)
            }
        }
        return false
    }

    override fun postTransform(transformInvocation: TransformInvocation) {
        actuatorSet.forEach {
            it.postTransform(transformInvocation)
        }
    }

    override fun preTransform(transformInvocation: TransformInvocation) {
        actuatorSet.forEach {
            it.preTransform(transformInvocation)
        }
    }

    abstract fun getTransformActuatorSet(): Set<TransformActuator>
}