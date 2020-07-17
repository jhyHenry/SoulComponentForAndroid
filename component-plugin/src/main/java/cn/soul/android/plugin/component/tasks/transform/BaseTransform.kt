package cn.soul.android.plugin.component.tasks.transform

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.utils.FileUtils
import java.io.File

/**
 * @author panxinghai
 *
 * date : 2019-07-19 18:10
 */
abstract class BaseTransform : Transform() {

    /**
     * 1、CONTENT_CLASS：表示需要处理 java 的 class 文件。
     * 2、CONTENT_JARS：表示需要处理 java 的 class 与 资源文件。
     * 3、CONTENT_RESOURCES：表示需要处理 java 的资源文件。
     * 4、CONTENT_NATIVE_LIBS：表示需要处理 native 库的代码。
     * 5、CONTENT_DEX：表示需要处理 DEX 文件。
     * 6、CONTENT_DEX_WITH_RESOURCES：表示需要处理 DEX 与 java 的资源文件。
     */
    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return TransformManager.CONTENT_JARS
    }

    /**
     * 表示是否支持增量更新
     */
    override fun isIncremental(): Boolean {
        return true
    }

    /**
     * 1、PROJECT：只有项目内容。
     * 2、SUB_PROJECTS：只有子项目。
     * 3、EXTERNAL_LIBRARIES：只有外部库，
     * 4、TESTED_CODE：由当前变体（包括依赖项）所测试的代码。
     * 5、PROVIDED_ONLY：只提供本地或远程依赖项。
     *
     * SCOPE_FULL_PROJECT 是一个 Scope 集合，包含 Scope.PROJECT,Scope.SUB_PROJECTS, Scope.EXTERNAL_LIBRARIES 这三项，
     * 即当前 Transform的作用域包括当前项目、子项目以及外部的依赖库
     */
    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    protected fun outputFiles(provider: TransformOutputProvider, directoryInput: DirectoryInput) {
        val dest = provider.getContentLocation(
                directoryInput.name,
                directoryInput.contentTypes,
                directoryInput.scopes,
                Format.DIRECTORY
        )
        // 将input的目录复制到output指定目录
        FileUtils.copyDirectory(directoryInput.file, dest)
    }

    protected fun getOutputDir(provider: TransformOutputProvider, directoryInput: DirectoryInput): File {
        return provider.getContentLocation(
                directoryInput.name,
                directoryInput.contentTypes,
                directoryInput.scopes,
                Format.DIRECTORY
        )
    }

    protected fun getOutputJar(provider: TransformOutputProvider, jarInput: JarInput): File {
        return provider.getContentLocation(
                jarInput.name,
                jarInput.contentTypes,
                jarInput.scopes,
                Format.JAR
        )
    }

    /**
     * 将input的目录复制到output指定目录
     */
    protected fun outputJarFile(provider: TransformOutputProvider, jarInput: JarInput) {
        val dest = provider.getContentLocation(
                jarInput.name,
                jarInput.contentTypes,
                jarInput.scopes,
                Format.JAR
        )
        FileUtils.copyFile(jarInput.file, dest)
    }

}