package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.plugin.component.resolve.ZipHelper
import com.android.build.api.transform.TransformInvocation
import java.io.File
import java.util.zip.ZipEntry

/**
 * @author panxinghai
 *
 * date : 2019-11-18 19:56
 */
abstract class TypeActuator(protected val isComponent: Boolean) : TransformActuator {

    override fun onJarVisited(jarFile: File, transformInvocation: TransformInvocation) {
        ZipHelper.traversalZip(jarFile) { entry ->
            onJarEntryVisited(entry, jarFile, transformInvocation)
        }
    }

    abstract fun onJarEntryVisited(zipEntry: ZipEntry,
                                   jarFile: File,
                                   transformInvocation: TransformInvocation)
}