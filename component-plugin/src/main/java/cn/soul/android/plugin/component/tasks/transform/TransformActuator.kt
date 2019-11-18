package cn.soul.android.plugin.component.tasks.transform

import com.android.build.api.transform.TransformInvocation
import javassist.CtClass
import java.util.zip.ZipEntry

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
     * @return if true, this ctClass was modified, actuator will write it to file
     */
    fun onClassVisited(ctClass: CtClass,
                       transformInvocation: TransformInvocation): Boolean

    fun onJarEntryVisited(zipEntry: ZipEntry,
                          transformInvocation: TransformInvocation)

    /**
     * see [BaseTraversalTransform.postTransform]
     */
    fun postTransform(transformInvocation: TransformInvocation)
}