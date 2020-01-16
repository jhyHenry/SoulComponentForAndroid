package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.plugin.component.utils.Log
import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.Status
import com.android.build.api.transform.TransformInvocation
import org.apache.commons.io.FileUtils
import java.io.File

/**
 * Base class for incremental compile transform.
 * @author panxinghai
 *
 * date : 2020-01-13 13:54
 */
abstract class BaseIncrementalTransform : BaseTransform() {
    override fun transform(transformInvocation: TransformInvocation?) {
        if (transformInvocation == null) {
            return
        }
        val isIncremental = transformInvocation.isIncremental
        val outputProvider = transformInvocation.outputProvider
        if (!isIncremental) {
            Log.p("is not incremental")
            outputProvider.deleteAll()
        }
        transformInvocation.inputs.forEach {
            it.jarInputs.forEach { jarInput ->
                val dest = getOutputJar(outputProvider, jarInput)
                when (jarInput.status) {
                    Status.ADDED, Status.CHANGED -> {
                        Log.e("jar changed:" + jarInput.file.absolutePath)
                        onChangedJarTransform(jarInput, dest)
                    }
                    Status.REMOVED -> {
                        Log.e("jar removed:" + jarInput.file.absolutePath)
                        if (dest.exists()) {
                            FileUtils.forceDelete(dest)
                        }
                    }
                    else -> {
                        Log.e("jar no changed:" + jarInput.file.absolutePath)
                        onJarTransform(jarInput, dest)
                    }
                }
            }
            it.directoryInputs.forEach dirInput@{ dirInput ->
                val outputDir = getOutputDir(outputProvider, dirInput)
                if (!isIncremental) {
                    onDirTransform(dirInput.file, outputDir)
                    return@dirInput
                }
                onIncrementalDirInput(outputDir, dirInput)
            }
        }
    }

    private fun onIncrementalDirInput(outputDir: File, dirInput: DirectoryInput) {
        val srcPath = dirInput.file.absolutePath
        val destPath = outputDir.absolutePath
        val executorList = mutableListOf<() -> Unit>()
        dirInput.changedFiles.forEach { (file, status) ->
            val destClassFilePath = file.absolutePath.replace(srcPath, destPath)
            val destFile = File(destClassFilePath)
            when (status) {
                Status.ADDED, Status.CHANGED -> {
                    Log.e("dir changed:${file.absolutePath}")
                    executorList.add {
                        onSingleFileTransform(status, file, outputDir, destFile)
                    }
                }
                Status.REMOVED -> {
                    Log.e("dir removed:${file.absolutePath}")
                    onRemovedFileTransform(outputDir, destFile)
                    if (destFile.exists()) {
                        FileUtils.forceDelete(destFile)
                    }
                }
                else -> {
                    Log.e("dir no changed:${file.absolutePath}")
                }
            }
        }
        executorList.forEach {
            it.invoke()
        }
    }

    abstract fun onJarTransform(jarInput: JarInput, destFile: File)

    abstract fun onChangedJarTransform(jarInput: JarInput, destFile: File)

    open fun onDirTransform(inputDir: File, outputDir: File) {
        FileUtils.copyDirectory(inputDir, outputDir)
    }

    open fun onSingleFileTransform(status: Status, inputFile: File, outputDir: File, destFile: File) {
        FileUtils.copyFile(inputFile, destFile)
    }

    open fun onRemovedFileTransform(outputDir: File, destFile: File) {

    }
}