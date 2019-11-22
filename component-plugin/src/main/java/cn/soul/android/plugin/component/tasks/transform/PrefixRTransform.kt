package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.plugin.component.resolve.PrefixHelper
import cn.soul.android.plugin.component.utils.InjectHelper
import cn.soul.android.plugin.component.utils.Log
import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import javassist.CtClass
import javassist.expr.ExprEditor
import javassist.expr.FieldAccess
import org.gradle.api.Project

/**
 * Created by nebula on 2019-08-20
 */
class PrefixRTransform(private val project: Project) : LibraryTraversalTransform() {
    private var applicationId = ""

    override fun preTraversal(transformInvocation: TransformInvocation) {
        prefix = PrefixHelper.instance.prefix
        InjectHelper.instance.refresh()
    }

    override fun preTransform(transformInvocation: TransformInvocation) {
        val p = project
        val variantName = transformInvocation.context.variantName
        val libPlugin = p.plugins.getPlugin(LibraryPlugin::class.java) as LibraryPlugin
        (libPlugin.extension as LibraryExtension).libraryVariants.all {
            if (it.name == variantName) {
                applicationId = it.applicationId
                Log.d("applicationId:$applicationId")
            }
        }
        val rCtClass = InjectHelper.instance.getClassPool()["$applicationId.R"]
        prefixCustomCtClassField(rCtClass)
    }

    override fun onDirVisited(dirInput: DirectoryInput, transformInvocation: TransformInvocation): Boolean {
        InjectHelper.instance.processFiles(dirInput.file)
                .nameFilter { file -> file.name.endsWith(".class") }
                .forEach {
                    prefixRClassFieldAccess(it, applicationId)
                    val dest = getOutputFile(transformInvocation.outputProvider, dirInput)
                    it.writeFile(dest.absolutePath)
                }
        return true
    }

    override fun onJarVisited(jarInput: JarInput, transformInvocation: TransformInvocation): Boolean {
        return true
    }

    override fun postTransform(transformInvocation: TransformInvocation) {
    }

    private var prefix: String? = null

    override fun getName(): String {
        return "prefixR"
    }

    fun setPrefix(prefix: String?) {
        this.prefix = prefix
    }

    private fun prefixCustomCtClassField(ctClass: CtClass) {
        Log.d("prefix R.class field access. which class is: ${ctClass.name}")
        ctClass.nestedClasses.forEach {
            it.fields.forEach { ctField ->
                if (it.isFrozen) {
                    it.defrost()
                }
                //eg:it.simpleName = "R$id"
                if (PrefixHelper.instance.isRefNeedPrefix(it.simpleName.substring(2), ctField.name)) {
                    ctField.name = "$prefix${ctField.name}"
                }
            }
        }
    }

    private fun prefixRClassFieldAccess(ctClass: CtClass, applicationId: String) {
        if (prefix == null) {
            return
        }
        if (isRFile(ctClass.simpleName)) {
            //skip R.class's field access prefix
            return
        }
        if (ctClass.isFrozen) {
            ctClass.defrost()
        }
        ctClass.instrument(object : ExprEditor() {
            override fun edit(f: FieldAccess?) {
                super.edit(f)
                if (f == null) {
                    return
                }
                if (f.isReader && needPrefix(f.className, f.fieldName, applicationId)) {
                    f.replace("\$_ = ${f.className}.$prefix${f.fieldName};")
                }
            }
        })
    }

    private fun isRFile(name: String): Boolean = name == "R" || name.startsWith("R$")

    private fun needPrefix(fullName: String, ref: String, applicationId: String): Boolean {
        if (!isCustomRFile(fullName, applicationId)) {
            return false
        }
        val strings = fullName.split('$')
        if (strings.size <= 1) {
            return false
        }
        val rName = strings[1]
        return PrefixHelper.instance.isRefNeedPrefix(rName, ref)
    }

    private fun isCustomRFile(name: String, applicationId: String) = name.startsWith("$applicationId.R")

}