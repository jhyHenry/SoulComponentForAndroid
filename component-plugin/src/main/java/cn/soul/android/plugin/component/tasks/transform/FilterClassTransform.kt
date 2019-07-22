package cn.soul.android.plugin.component.tasks.transform

import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.utils.FileUtils


/**
 * Created by nebula on 2019-07-22
 */
class FilterClassTransform : BaseTransform() {
    override fun transform(transformInvocation: TransformInvocation?) {
        super.transform(transformInvocation)
        val inputs = transformInvocation?.inputs ?: return
        inputs.forEach { input ->
            input.directoryInputs.forEach { dirInput ->
                val dest = getOutputFile(transformInvocation.outputProvider, dirInput)
                dirInput.file.listFiles()?.forEach inner@{
                    if (!it.isDirectory) {
                        return@inner
                    }
                    FileUtils.copyDirectory(it, dest)
                }
            }
            //ignore jar file
        }
    }

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return TransformManager.CONTENT_JARS
    }

    override fun getName(): String {
        return "filterClass"
    }

}