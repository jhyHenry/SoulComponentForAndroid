package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.plugin.component.resolve.PrefixHelper
import cn.soul.android.plugin.component.utils.InjectHelper
import cn.soul.android.plugin.component.utils.Log
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import javassist.CtClass
import javassist.expr.ExprEditor
import javassist.expr.FieldAccess
import org.gradle.api.Project
import java.util.zip.ZipEntry

/**
 * @author panxinghai
 *
 * date : 2019-11-19 11:05
 */
class PrefixRActuator(private val project: Project,
                      isComponent: Boolean) : TypeActuator(isComponent) {
    private var applicationId = ""
    private var prefix: String? = null

    override fun preTraversal(transformInvocation: TransformInvocation) {
        prefix = PrefixHelper.instance.prefix
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
//        val rCtClass = InjectHelper.instance.getClassPool()["$applicationId.R"]
//        prefixCustomCtClassField(rCtClass, transformInvocation)
    }

    override fun onClassVisited(ctClass: CtClass, transformInvocation: TransformInvocation): Boolean {
        prefixRClassFieldAccess(ctClass, applicationId)
        return true
    }

    override fun onJarEntryVisited(zipEntry: ZipEntry, transformInvocation: TransformInvocation) {
        println(zipEntry.name)
    }

    override fun postTransform(transformInvocation: TransformInvocation) {
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
                if (f == null) {
                    return
                }
                if (f.isReader && needPrefix(f.className, f.fieldName, applicationId)) {
                    val clazz = f.className
                    val field = "$prefix${f.fieldName}"
                    println("{\$_ = ${f.className}.$prefix${f.fieldName};}")
                    f.replace("{\$_ = ${f.className}.$prefix${f.fieldName};}")
//                    f.replace("\$_ = \$proceed(\$\$);")
                    println(InjectHelper.instance.getClassPool()[clazz].getField(field).modifiers.toString())
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