package cn.soul.android.plugin.component.tasks.transform

import com.android.build.api.transform.TransformInvocation
import javassist.CtClass
import org.gradle.api.Project
import java.util.zip.ZipEntry

/**
 * @author panxinghai
 *
 * date : 2019-11-19 11:05
 */
class PrefixActuator(private val project: Project,
                     isComponent: Boolean) : TypeActuator(isComponent) {
    override fun preTraversal(transformInvocation: TransformInvocation) {
    }

    override fun preTransform(transformInvocation: TransformInvocation) {
    }

    override fun onClassVisited(ctClass: CtClass, transformInvocation: TransformInvocation): Boolean {
        return false
    }

    override fun onJarEntryVisited(zipEntry: ZipEntry, transformInvocation: TransformInvocation) {
    }

    override fun postTransform(transformInvocation: TransformInvocation) {
    }
}