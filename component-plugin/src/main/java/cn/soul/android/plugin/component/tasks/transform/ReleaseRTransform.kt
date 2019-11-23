package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.plugin.component.resolve.PrefixHelper
import cn.soul.android.plugin.component.resolve.ZipHelper
import cn.soul.android.plugin.component.utils.Descriptor
import cn.soul.android.plugin.component.utils.InjectHelper
import cn.soul.android.plugin.component.utils.Log
import com.android.build.api.transform.*
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.internal.pipeline.TransformManager
import com.google.common.collect.ImmutableSet
import javassist.CtClass
import org.gradle.api.Project
import java.util.zip.ZipEntry

/**
 * @author panxinghai
 *
 * date : 2019-10-14 20:14
 */
class ReleaseRTransform(private val project: Project) : BaseTraversalTransform() {
    override fun onJarVisited(jarInput: JarInput, transformInvocation: TransformInvocation): Boolean {
        val dest = transformInvocation.outputProvider.getContentLocation(
                "releaseR",
                TransformManager.CONTENT_CLASS,
                TransformManager.PROJECT_ONLY,
                Format.DIRECTORY)
        ZipHelper.traversalZip(jarInput.file) {
            val clazz = InjectHelper.instance.getClassPool()[Descriptor.getClassNameByFileName(it.name)]
            clazz.writeFile(dest.absolutePath)
        }
        return true
    }

    override fun preTransform(transformInvocation: TransformInvocation) {
        super.preTransform(transformInvocation)
        var applicationId = ""
        val variantName = transformInvocation.context.variantName
        val libPlugin = project.plugins.getPlugin(LibraryPlugin::class.java) as LibraryPlugin
        (libPlugin.extension as LibraryExtension).libraryVariants.all {
            if (it.name == variantName) {
                applicationId = it.applicationId
                Log.d("applicationId:$applicationId")
            }
        }
        val rCtClass = InjectHelper.instance.getClassPool()["$applicationId.R"]
        prefixCustomCtClassField(rCtClass, transformInvocation)
    }

    private fun prefixCustomCtClassField(ctClass: CtClass, transformInvocation: TransformInvocation) {
        Log.d("prefix R.class field access. which class is: ${ctClass.name}")
        ctClass.nestedClasses.forEach {
            it.fields.forEach { ctField ->
                if (it.isFrozen) {
                    it.defrost()
                }
                //eg:it.simpleName = "R$id"
                if (PrefixHelper.instance.isRefNeedPrefix(it.simpleName.substring(2), ctField.name)) {
                    ctField.name = "${PrefixHelper.instance.prefix}${ctField.name}"
                }
            }
        }
    }

    override fun postTransform(transformInvocation: TransformInvocation) {

    }

    override fun onDirVisited(dirInput: DirectoryInput, transformInvocation: TransformInvocation): Boolean {
        return false
    }


    override fun preTraversal(transformInvocation: TransformInvocation) {
        InjectHelper.instance.refresh()
        super.preTraversal(transformInvocation)
    }

    override fun getName(): String {
        return "releaseJarLib"
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return TransformManager.PROJECT_ONLY
    }
}