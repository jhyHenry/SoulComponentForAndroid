package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.plugin.component.resolve.PrefixHelper
import cn.soul.android.plugin.component.utils.InjectHelper
import cn.soul.android.plugin.component.utils.Log
import com.android.build.api.transform.Status
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.internal.scope.InternalArtifactType
import javassist.CtClass
import javassist.CtField
import javassist.expr.ExprEditor
import javassist.expr.FieldAccess
import org.gradle.api.Project
import org.gradle.api.file.FileSystemLocation
import java.io.File
import java.util.zip.ZipEntry

/**
 *
 * R 资源动态添加前缀
 *
 * @author panxinghai
 *
 * date : 2019-11-19 11:05
 */
class PrefixRActuator(private val project: Project, isComponent: Boolean) : TypeActuator(isComponent) {

    private var applicationId = ""
    private var prefix: String? = null

    // 处理遍历
    override fun preTraversal(transformInvocation: TransformInvocation) {
        prefix = PrefixHelper.instance.prefix
    }

    // 转换，通过 javassist 动态修改 R 文件资源 ID 前缀
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
        libPlugin.variantManager.variantScopes.forEach {
            if (it.fullVariantName != variantName) {
                return@forEach
            }
            // 获取到 R.jar
            val provider = it.artifacts.getFinalProduct<FileSystemLocation>(InternalArtifactType.COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR)
            InjectHelper.instance.appendClassPath(provider.get().asFile.absolutePath)
        }
        // 处理 R.class 添加前缀
        val rCtClass = InjectHelper.instance.getClassPool()["$applicationId.R"]
        prefixCustomCtClassField(rCtClass)
    }

    /**
     * 判断是否要直接写入 or 修改重写
     */
    override fun onClassVisited(ctClass: CtClass): Boolean {
        return onIncrementalClassVisited(Status.ADDED, ctClass)
    }

    override fun onIncrementalClassVisited(status: Status, ctClass: CtClass): Boolean {
        return prefixRClassFieldAccess(ctClass, applicationId)
    }

    override fun onRemovedClassVisited(ctClass: CtClass) {
    }

    override fun onJarEntryVisited(zipEntry: ZipEntry, jarFile: File) {
    }

    override fun postTransform(transformInvocation: TransformInvocation) {
    }

    /**
     * R.class 资源添加前缀字段
     */
    private fun prefixCustomCtClassField(ctClass: CtClass) {
        Log.d("prefix R.class field access. which class is: ${ctClass.name}")
        val classInfo = arrayListOf<Pair<String, MutableList<String>>>()
        ctClass.nestedClasses.forEach {
            val pair = Pair(it.name, arrayListOf<String>())
            it.fields.forEach { ctField ->
                // 如果类被写入不可修复，设置为可修改状态
                if (it.isFrozen) {
                    it.defrost()
                }
                // eg:it.simpleName = "R$id" 动态添加前缀
                if (PrefixHelper.instance.isRefNeedPrefix(it.simpleName.substring(2), ctField.name)) {
                    pair.second.add("public static ${ctField.type.name} $prefix${ctField.name};")
                } else {
                    pair.second.add("public static ${ctField.type.name} ${ctField.name};")
                }
            }
            classInfo.add(pair)
        }
        ctClass.detach()
        /* with gradle 3.3.0, R file changed from .java file to .jar file, if directly use
         * fieldAccess replace, new access while inline by constant, so construct new ctclass
         * in javassist, it will be correct behavior
         */
        classInfo.forEach {
            val newRClass = InjectHelper.instance.getClassPool().makeClass(it.first)
            it.second.forEach { field ->
                newRClass.addField(CtField.make(field, newRClass))
            }
        }
    }

    /**
     * res 资源增加前缀
     */
    private fun prefixRClassFieldAccess(ctClass: CtClass, applicationId: String): Boolean {
        if (prefix == null) {
            return false
        }
        if (isRFile(ctClass.simpleName)) {
            // 跳过 R.class's 类型的访问前缀
            return false
        }
        // 强制制为可修改状态
        if (ctClass.isFrozen) {
            ctClass.defrost()
        }
        var modify = false
        // 遍历修改字段
        ctClass.instrument(object : ExprEditor() {
            override fun edit(fa: FieldAccess?) {
                if (fa == null) {
                    return
                }
                if (fa.isReader && needPrefix(fa.className, fa.fieldName, applicationId)) {
                    Log.d("{\$_ = ${fa.className}.$prefix${fa.fieldName};}")
                    fa.replace("{\$_ = ${fa.className}.$prefix${fa.fieldName};}")
                    modify = true
                }
            }
        })
        return modify
    }

    /**
     * 判断资源 R 文件
     */
    private fun isRFile(name: String): Boolean = name == "R" || name.startsWith("R$")

    /**
     * 判断是否需要增加前缀
     */
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

    /**
     * 自定义 R 文件
     */
    private fun isCustomRFile(name: String, applicationId: String) = name.startsWith("$applicationId.R")

}