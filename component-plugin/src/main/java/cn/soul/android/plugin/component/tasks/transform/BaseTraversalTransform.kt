package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.plugin.component.utils.InjectHelper
import cn.soul.android.plugin.component.utils.Log
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.TransformInvocation
import javassist.CtClass
import java.io.File

/**
 * @author panxinghai
 *
 * date : 2019-09-09 18:06
 */
abstract class BaseTraversalTransform : BaseIncrementalTransform() {
    private var timeCost = 0L
    override fun transform(transformInvocation: TransformInvocation?) {
        val current = System.currentTimeMillis()
        val inputs = transformInvocation?.inputs ?: return

        preTraversal(transformInvocation)
        inputs.forEach { input ->
            input.directoryInputs.forEach { dirInput ->
                InjectHelper.instance.appendClassPath(dirInput.file.absolutePath)
            }
            input.jarInputs.forEach {
                InjectHelper.instance.appendClassPath(it.file.absolutePath)
            }
        }

        preTransform(transformInvocation)
        super.transform(transformInvocation)
        postTransform(transformInvocation)
        timeCost = System.currentTimeMillis() - current
        Log.p("transform time cost: ${timeCost}ms")
    }

    override fun onSingleFileTransform(inputFile: File, outputDir: File, destFile: File) {
        if (!onChangedFile(inputFile, outputDir, destFile)) {
            super.onSingleFileTransform(inputFile, outputDir, destFile)
        }
    }

    override fun onDirTransform(inputDir: File, outputDir: File) {
        val srcPath = inputDir.absolutePath
        val destPath = outputDir.absolutePath

        InjectHelper.instance.processFiles(inputDir)
                .nameFilter { file -> file.name.endsWith(".class") }
                .forEach { ctClass, file ->
                    if (onInputFileVisited(ctClass, outputDir)) {
                        ctClass.writeFile(outputDir.absolutePath)
                    } else {
                        val destClassFilePath = file.absolutePath.replace(srcPath, destPath)
                        val destFile = File(destClassFilePath)
                        super.onSingleFileTransform(file, outputDir, destFile)
                    }
                }
    }

    override fun onJarVisited(jarInput: JarInput, destFile: File) {
    }

    override fun onChangedJarVisited(jarInput: JarInput, destFile: File) {
    }

    abstract fun onInputFileVisited(ctClass: CtClass, outputDir: File): Boolean

    /**
     * visitor method of input file
     * @param inputFile input File
     * @return if return false , sub class will not consume file input, [BaseTraversalTransform]
     * will write result to target directory for next transform. Otherwise [BaseTraversalTransform]
     * will ignore build result.
     */
    abstract fun onChangedFile(inputFile: File, outputDir: File, destFile: File): Boolean

    /**
     * see [onInputFileVisited]
     */
    abstract fun onJarVisited(jarInput: JarInput): Boolean

    /**
     * [BaseTraversalTransform] will traversal first to load all input files to Javassist ClassPool,
     * this method run before the traversal.
     */
    protected open fun preTraversal(transformInvocation: TransformInvocation) {

    }

    /**
     * called before all [onInputFileVisited] and [onJarVisited]
     */
    protected open fun preTransform(transformInvocation: TransformInvocation) {

    }

    /**
     * called after all [onInputFileVisited] and [onJarVisited]
     */
    abstract fun postTransform(transformInvocation: TransformInvocation)
}