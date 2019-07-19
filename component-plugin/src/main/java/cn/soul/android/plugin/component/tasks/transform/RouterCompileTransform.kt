package cn.soul.android.plugin.component.tasks.transform

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
                println(dirInput.file.absolutePath)
                outputFiles(transformInvocation.outputProvider, dirInput)
            }
            input.jarInputs.forEach {
                println(it.file.absolutePath)
                outputJarFile(transformInvocation.outputProvider, it)
            }
        }
    }

    override fun getName(): String {
        return "routerCompile"
    }
}