package cn.soul.android.plugin.component

import com.android.SdkConstants.*
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.*
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.builder.core.DefaultManifestParser
import com.android.builder.core.ManifestAttributeSupplier
import com.android.builder.core.VariantTypeImpl
import com.android.utils.FileUtils
import com.android.utils.StringHelper
import org.gradle.api.Task
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileCollection
import java.io.File
import java.util.*

/**
 * @author panxinghai
 *
 * date : 2019-07-11 15:33
 */
class PluginVariantScopeImpl(private val scope: VariantScope, private val globalScope: GlobalScope, private val extensions: BaseExtension) : PluginVariantScope {
    private val taskContainer = PluginTaskContainer(scope.taskContainer)

    override fun getTaskContainer(): PluginTaskContainer {
        return taskContainer
    }

    override fun getTaskName(prefix: String, suffix: String): String {
        return scope.getTaskName("component${prefix.capitalize()}", suffix)
    }

    override fun getTaskName(prefix: String): String {
        return getTaskName(prefix, "")
    }

    override fun getFullName(): String {
        return getVariantConfiguration().fullName
    }

    override fun getVariantData(): BaseVariantData {
        return scope.variantData
    }

    override fun getGlobalScope(): GlobalScope {
        return globalScope
    }

    override fun getAnnotationProcessorOutputDir(): File {
        return FileUtils.join(
                getGeneratedDir(),
                "source",
                "apt",
                getVariantConfiguration().dirName)
    }

    override fun getBundleArtifactFolderForDataBinding(): File {
        return dataBindingIntermediate("bundle-bin")
    }

    override fun getAarClassesJar(): File {
        return intermediate("packaged-classes", FN_CLASSES_JAR)
    }

    override fun getIntermediateJarOutputFolder(): File {
        return File(getIntermediatesDir(), "/intermediate-jars/${getFullName()}")
    }

    override fun keepDefaultBootstrap(): Boolean {
        return scope.keepDefaultBootstrap()
    }

    override fun getJava8LangSupportType(): VariantScope.Java8LangSupport {
        return scope.java8LangSupportType
    }

    override fun getVariantConfiguration(): GradleVariantConfiguration {
        val realConfig = scope.variantData.variantConfiguration
        val variantConfig = GradleVariantConfiguration.getBuilderForExtension(extensions)
                .create(
                        globalScope.projectOptions,
                        realConfig.defaultConfig,
                        realConfig.defaultSourceSet,
                        getParser(realConfig.defaultSourceSet.manifestFile, globalScope),
                        realConfig.buildType,
                        realConfig.buildTypeSourceSet,
                        VariantTypeImpl.LIBRARY,
                        realConfig.signingConfig,
                        globalScope.errorHandler,
                        this::canParseManifest)
        return variantConfig
    }

    override fun getAidlSourceOutputDir(): File {
        return File(getGeneratedDir(), "source/aidl/${getVariantConfiguration().dirName}")
    }

    override fun getArtifactFileCollection(configType: AndroidArtifacts.ConsumedConfigType, artifactScope: AndroidArtifacts.ArtifactScope, artifactType: AndroidArtifacts.ArtifactType, attributeMap: Map<Attribute<String>, String>?): FileCollection {
        return scope.getArtifactFileCollection(configType, artifactScope, artifactType, attributeMap)
    }

    override fun getArtifactFileCollection(configType: AndroidArtifacts.ConsumedConfigType, artifactScope: AndroidArtifacts.ArtifactScope, artifactType: AndroidArtifacts.ArtifactType): FileCollection {
        return getArtifactFileCollection(configType, artifactScope, artifactType, null)
    }

    override fun getArtifactCollection(configType: AndroidArtifacts.ConsumedConfigType, artifactScope: AndroidArtifacts.ArtifactScope, artifactType: AndroidArtifacts.ArtifactType): ArtifactCollection {
        return scope.getArtifactCollection(configType, artifactScope, artifactType)
    }

    override fun getBootClasspath(): FileCollection {
        return scope.bootClasspath
    }

    override fun getJavaClasspath(configType: AndroidArtifacts.ConsumedConfigType, classesType: AndroidArtifacts.ArtifactType): FileCollection {
        return scope.getJavaClasspath(configType, classesType)
    }

    override fun getArtifacts(): BuildArtifactsHolder {
        return scope.artifacts
    }

    override fun getDefaultMergeResourcesOutputDir(): File {
        return FileUtils.join(getIntermediatesDir(),
                FD_RES,
                FD_MERGED,
                getVariantConfiguration().dirName)
    }

    override fun getIntermediateDir(type: InternalArtifactType): File {
        return File(getIntermediatesDir(), "${type.name.toLowerCase(Locale.US)}/${getVariantConfiguration().dirName}")
    }

    override fun getResourceBlameLogDir(): File {
        return FileUtils.join(getIntermediatesDir(),
                StringHelper.toStrings(
                        "blame",
                        "res",
                        getVariantConfiguration().directorySegments))
    }

    override fun isCrunchPngs(): Boolean {
        return scope.isCrunchPngs
    }

    override fun useResourceShrinker(): Boolean {
        return scope.useResourceShrinker()
    }

    override fun getGeneratedDir(): File {
        return File(getScopeBuildDir(), "generated")
    }

    override fun getOutputsDir(): File {
        return File(getGeneratedDir(), "outputs")
    }

    override fun getRenderscriptResOutputDir(): File {
        return getGeneratedResourcesDir("rs")
    }

    override fun getGeneratedResOutputDir(): File {
        return getGeneratedResourcesDir("resValues")
    }

    override fun getGeneratedPngsOutputDir(): File {
        return getGeneratedResourcesDir("pngs")
    }

    override fun getAarLocation(): File {
        return FileUtils.join(getGeneratedDir(), "aar")
    }

    override fun getOutputScope(): OutputScope {
        return scope.outputScope
    }

    override fun getJavaClasspathArtifacts(configType: AndroidArtifacts.ConsumedConfigType, classesType: AndroidArtifacts.ArtifactType, generatedBytecodeKey: Any?): ArtifactCollection {
        return scope.getJavaClasspathArtifacts(configType, classesType, generatedBytecodeKey)
    }

    override fun getScopeBuildDir(): File {
        return File(globalScope.buildDir, Constants.BUILD_FOLDER_NAME)
    }

    override fun getIntermediatesDir(): File {
        return File(getScopeBuildDir(), "intermediates")
    }

    override fun getIncrementalDir(name: String): File {
        return File(getIntermediatesDir(), "incremental/$name")
    }

    override fun getInternalArtifactTypeOutputFile(type: InternalArtifactType, task: Task, fileName: String): File {
        return File(getIntermediateDir(type), "${task.name}/$fileName")
    }

    private fun getGeneratedResourcesDir(name: String): File {
        return FileUtils.join(
                getGeneratedDir(),
                StringHelper.toStrings(
                        "res",
                        name,
                        getVariantConfiguration().directorySegments))
    }

    private val manifestParserMap = mutableMapOf<File, ManifestAttributeSupplier>()
    private fun getParser(file: File, globalScope: GlobalScope): ManifestAttributeSupplier {
        return (manifestParserMap).computeIfAbsent(
                file
        ) { f ->
            DefaultManifestParser(
                    f,
                    this::canParseManifest,
                    globalScope.androidBuilder.issueReporter)
        }
    }

    private fun canParseManifest(): Boolean = false

    private fun intermediate(directoryName: String, fileName: String): File {
        return FileUtils.join(
                getIntermediatesDir(),
                directoryName,
                getVariantConfiguration().dirName,
                fileName)
    }

    private fun dataBindingIntermediate(name: String): File {
        return intermediate("data-binding", name)
    }
}