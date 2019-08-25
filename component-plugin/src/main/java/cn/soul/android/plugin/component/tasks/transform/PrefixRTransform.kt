package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.component.annotation.Router
import cn.soul.android.plugin.component.utils.InjectHelper
import cn.soul.android.plugin.component.utils.Log
import com.android.build.api.transform.Format
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import javassist.CtClass

/**
 * Created by nebula on 2019-08-20
 */
class PrefixRTransform : BaseTransform() {
    override fun getName(): String {
        return "prefixR"
    }

    override fun transform(transformInvocation: TransformInvocation?) {
        super.transform(transformInvocation)
        val inputs = transformInvocation?.inputs ?: return
        inputs.forEach { input ->
            input.directoryInputs.forEach { dirInput ->
                InjectHelper.instance.appendClassPath(dirInput.file.absolutePath)
                InjectHelper.instance.processFiles(dirInput.file)
                        .nameFilter { file -> file.name.endsWith(".class") }
                        .forEach {
                            //                            val routerAnnotation = it.getAnnotation(Router::class.java) as Router
//                            val path = routerAnnotation.path
                            Log.e("ctClass:${it.name}")
                            it.refClasses.forEach { ref ->
                                if (ref is String) {
                                    Log.e("ref:${ref}")
                                } else {
                                    Log.e("class:${ref?.javaClass?.name}")
                                }
                            }
                        }
                outputFiles(transformInvocation.outputProvider, dirInput)
            }
            input.jarInputs.forEach {
                InjectHelper.instance.appendClassPath(it.file.absolutePath)
                outputJarFile(transformInvocation.outputProvider, it)
            }
        }
        val dest = transformInvocation.outputProvider.getContentLocation(
                "prefixR",
                TransformManager.CONTENT_CLASS,
                TransformManager.PROJECT_ONLY,
                Format.DIRECTORY)
    }
}