package cn.soul.android.plugin.component.utils.javassist

import cn.soul.android.plugin.component.utils.InjectHelper
import cn.soul.android.plugin.component.utils.Log
import javassist.CtClass
import javassist.CtMethod

/**
 * @author panxinghai
 *
 * date : 2019-10-29 23:00
 */
class MethodGen(private val className: String) {
    private var returnStatement = ""
    private var name = ""
    private var paramsStatement = ""
    private var methodBodyProvider: () -> String = { "{}" }
    private var interfaces: Array<out CtClass> = arrayOf()

    fun signature(returnStatement: String = "void", name: String, paramsStatement: String = "()"): MethodGen {
        this.returnStatement = returnStatement
        this.name = name
        this.paramsStatement = paramsStatement
        return this
    }

    fun body(provider: () -> String): MethodGen {
        this.methodBodyProvider = provider
        return this
    }

    fun superClass(superClass: CtClass): MethodGen {
        return this
    }

    fun interfaces(vararg interfaces: CtClass) {
        this.interfaces = interfaces
    }

    fun gen(): CtClass? {
        try {
            val classPool = InjectHelper.instance.getClassPool()
            var genClass: CtClass? = classPool.getOrNull(className)
            if (genClass == null) {
                genClass = classPool.makeClass(className)
                interfaces.forEach {
                    genClass.addInterface(it)
                }
                genClass.addMethod(genGatherTasksMethod(genClass))
            } else {
                if (genClass.isFrozen) {
                    genClass.defrost()
                }
                genClass.getDeclaredMethod(name)
                        .setBody(methodBodyProvider.invoke())
            }
            return genClass
        } catch (e: Exception) {
            e.printStackTrace()
            if (e.message != null) {
                Log.e(e.message!!)
            }
        }
        return null
    }

    private fun genGatherTasksMethod(genClass: CtClass): CtMethod {
        return CtMethod.make(gatherTasksSrc(), genClass)
    }

    private fun gatherTasksSrc(): String {
        return "$returnStatement $name$paramsStatement${methodBodyProvider.invoke()}"
    }
}
