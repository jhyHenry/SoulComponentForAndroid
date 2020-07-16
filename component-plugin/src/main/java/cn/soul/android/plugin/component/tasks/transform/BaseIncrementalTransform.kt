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
        // 输出 provider 用于创建输出文件
        val outputProvider = transformInvocation.outputProvider
        // 是否热更
        val isIncremental = transformInvocation.isIncremental
        if (!isIncremental) {
            Log.p("is not incremental")
            outputProvider.deleteAll()
        }
        // Jar Or Dir
        transformInvocation.inputs.forEach {
            it.jarInputs.forEach { jarInput ->
                val dest = getOutputJar(outputProvider, jarInput)
                // khalaApp: jar => NOTCHANGED:/Users/walid/Desktop/dev/soul/android/DDComponentForAndroid/readercomponent/build/intermediates/merged_java_res/debug/out.jar
                Log.p(name, "jar => ${jarInput.status.name}:" + jarInput.file.absolutePath)
                when (jarInput.status) {
                    Status.ADDED, Status.CHANGED -> {
                        onIncrementalJarTransform(jarInput.status, jarInput, dest)
                    }
                    Status.REMOVED -> {
                        // it seemed transform will full build when remove jar file, so ignore this status
                        if (dest.exists()) {
                            FileUtils.forceDelete(dest)
                        }
                    }
                    else -> {
                        // TODO ?
                        if (!isIncremental) {
                            onIncrementalJarTransform(Status.ADDED, jarInput, dest)
                        }
                    }
                }
            }
            it.directoryInputs.forEach dirInput@{ dirInput ->
                // khalaApp: dir => /Users/walid/Desktop/dev/soul/android/DDComponentForAndroid/readercomponent/build/intermediates/javac/debug/classes
                Log.p(name, "dir => " + dirInput.file.absolutePath)
                val outputDir = getOutputDir(outputProvider, dirInput)
                if (!isIncremental) {
                    onDirTransform(dirInput.file, outputDir)
                    return@dirInput
                }
                onIncrementalDirTransform(outputDir, dirInput)
            }
        }
    }

    // 目录转换处理 class -> dex
    open fun onDirTransform(inputDir: File, outputDir: File) {
        FileUtils.copyDirectory(inputDir, outputDir)
    }

    private fun onIncrementalDirTransform(outputDir: File, dirInput: DirectoryInput) {
        val srcPath = dirInput.file.absolutePath
        val destPath = outputDir.absolutePath
        val executorList = mutableListOf<() -> Unit>()
        dirInput.changedFiles.forEach { (file, status) ->
            if (file.isDirectory) {
                return@forEach
            }
            val destClassFilePath = file.absolutePath.replace(srcPath, destPath)
            val destFile = File(destClassFilePath)
            when (status) {
                Status.ADDED, Status.CHANGED -> {
                    Log.d("dir ${status.name}:${file.absolutePath}")
                    executorList.add {
                        onSingleFileTransform(status, file, outputDir, destFile)
                    }
                }
                Status.REMOVED -> {
                    Log.d("dir removed:${file.absolutePath}")
                    onRemovedFileTransform(outputDir, destFile)
                    if (destFile.exists()) {
                        FileUtils.forceDelete(destFile)
                    }
                }
                else -> {
                    Log.test("dir no changed:${file.absolutePath}")
                }
            }
        }
        executorList.forEach {
            it.invoke()
        }
    }

    // 热更Jar转换处理
    open fun onIncrementalJarTransform(status: Status, jarInput: JarInput, destFile: File) {
        FileUtils.copyFile(jarInput.file, destFile)
    }

    // 单文件转换处理 class -> dex
    open fun onSingleFileTransform(status: Status, inputFile: File, outputDir: File, destFile: File) {
        FileUtils.copyFile(inputFile, destFile)
    }

    open fun onRemovedFileTransform(outputDir: File, destFile: File) {

    }

}