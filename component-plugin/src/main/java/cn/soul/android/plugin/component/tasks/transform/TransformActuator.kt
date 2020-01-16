package cn.soul.android.plugin.component.tasks.transform

import com.android.build.api.transform.Status
import com.android.build.api.transform.TransformInvocation
import javassist.CtClass
import java.io.File

/**
 * use this actuator decoupling logic.
 * @author panxinghai
 *
 * date : 2019-11-18 16:53
 */
interface TransformActuator {
    /**
     * see [BaseTraversalTransform.preTraversal]
     */
    fun preTraversal(transformInvocation: TransformInvocation)

    /**
     * see [BaseTraversalTransform.preTransform]
     */
    fun preTransform(transformInvocation: TransformInvocation)

    /**
     * @return if true, this ctClass was be modified, actuator will write it to file, otherwise actuator
     * will directly copy source file to destination directory.
     */
    fun onClassVisited(ctClass: CtClass): Boolean

    fun onIncrementalClassVisited(status: Status, ctClass: CtClass): Boolean

    fun onRemovedClassVisited(ctClass: CtClass)

    fun onJarVisited(jarFile: File)

    /**
     * see [BaseTraversalTransform.postTransform]
     */
    fun postTransform(transformInvocation: TransformInvocation)
}