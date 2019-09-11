package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.plugin.component.resolve.PrefixHelper
import cn.soul.android.plugin.component.utils.InjectHelper
import cn.soul.android.plugin.component.utils.Log
import com.android.build.api.transform.Format
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.internal.pipeline.TransformManager
import javassist.CtClass
import javassist.expr.ExprEditor
import javassist.expr.FieldAccess
import org.gradle.api.Project

/**
 * Created by nebula on 2019-08-20
 */
class PrefixRTransform(private val project: Project) : BaseTransform() {
    private var prefix: String? = null

    override fun getName(): String {
        return "prefixR"
    }

    override fun transform(transformInvocation: TransformInvocation?) {
        super.transform(transformInvocation)

        val p = project
        val inputs = transformInvocation?.inputs ?: return
        val variantName = transformInvocation.context.variantName
        val appPlugin = p.plugins.getPlugin(AppPlugin::class.java) as AppPlugin
        var applicationId = ""
        (appPlugin.extension as AppExtension).applicationVariants.all {
            if (it.name == variantName) {
                applicationId = it.applicationId
                println("applicationId:$applicationId")
            }
        }
        inputs.forEach { input ->
            input.directoryInputs.forEach { dirInput ->
                InjectHelper.instance.appendClassPath(dirInput.file.absolutePath)
            }
            input.jarInputs.forEach {
                InjectHelper.instance.appendClassPath(it.file.absolutePath)
            }
        }
        val rCtClass = InjectHelper.instance.getClassPool()["$applicationId.R"]
        prefixCustomCtClassField(rCtClass)
        inputs.forEach { input ->
            input.directoryInputs.forEach { dirInput ->
                InjectHelper.instance.processFiles(dirInput.file)
                        .nameFilter { file -> file.name.endsWith(".class") }
                        .forEach {
                            prefixRClassFieldAccess(it, applicationId)
                            val dest = getOutputFile(transformInvocation.outputProvider, dirInput)
                            it.writeFile(dest.absolutePath)
                        }
            }
            input.jarInputs.forEach {
                outputJarFile(transformInvocation.outputProvider, it)
            }
        }
        val dest = transformInvocation.outputProvider.getContentLocation(
                "prefixR",
                TransformManager.CONTENT_CLASS,
                TransformManager.PROJECT_ONLY,
                Format.DIRECTORY)
    }

    fun setPrefix(prefix: String?) {
        this.prefix = prefix
    }

    private fun prefixCustomCtClassField(ctClass: CtClass) {
        ctClass.nestedClasses.forEach {
            it.fields.forEach { ctField ->
                if (it.isFrozen) {
                    it.defrost()
                }
                ctField.name = "$prefix${ctField.name}"
            }
        }
    }

    private fun prefixRClassFieldAccess(ctClass: CtClass, applicationId: String) {
        if (prefix == null) {
            return
        }
        if (isRFile(ctClass.simpleName)) {
            Log.d("skip prefix R.class field access. which class is: ${ctClass.name}")
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