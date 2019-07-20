package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.component.annotation.Router
import cn.soul.android.plugin.component.utils.InjectHelper
import com.android.build.api.transform.TransformInvocation

/**
 * @author panxinghai
 *
 * date : 2019-07-19 18:09
 */
class RouterCompileTransform : BaseTransform() {
    override fun transform(transformInvocation: TransformInvocation?) {
        super.transform(transformInvocation)
        val inputs = transformInvocation?.inputs
        inputs?.forEach { input ->
            input.directoryInputs.forEach { dirInput ->
                InjectHelper.instance.appendClassPath(dirInput.file.absolutePath)
                println(dirInput.file.absolutePath)
                InjectHelper.instance.processFiles(dirInput.file)
                        .nameFilter { file -> file.name.endsWith(".class") }
                        .classFilter { ctClass ->
                            ctClass.annotations.forEach {
                                println(it.toString())
                            }
                            ctClass.annotations.contains(Router::class.java)
                        }.forEach {
                            println("router:${it.name}")
                        }
                outputFiles(transformInvocation.outputProvider, dirInput)
            }
            input.jarInputs.forEach {
//                println(it.file.absolutePath)
                outputJarFile(transformInvocation.outputProvider, it)
            }
        }
    }

    override fun getName(): String {
        return "routerCompile"
    }
}