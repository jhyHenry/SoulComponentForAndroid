package cn.soul.android.plugin.component

import cn.soul.android.plugin.component.extesion.ComponentExtension
import com.android.SdkConstants.*
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.*
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.builder.core.DefaultManifestParser
import com.android.builder.core.ManifestAttributeSupplier
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
@Suppress("UnstableApiUsage")
class PluginVariantScopeImpl(private val scope: VariantScope,
                             private val globalScope: GlobalScope,
                             extensions: BaseExtension,
                             private val transformManager: TransformManager,
                             private val compExtension: ComponentExtension) : PluginVariantScope {
    //    private var variantConfiguration: GradleVariantConfiguration
    private val manifestParserMap = mutableMapOf<File, ManifestAttributeSupplier>()

//    init {
//        val realConfig = scope.variantData.variantConfiguration
//        Log.e("realConfig: ${realConfig.buildType.name}")
//        Log.e("realFlavor: ${realConfig.defaultConfig.name}")
//        variantConfiguration = GradleVariantConfiguration.getBuilderForExtension(extensions)
//                .create(
//                        globalScope.projectOptions,
//                        realConfig.defaultConfig,
//                        realConfig.defaultSourceSet,
//                        getParser(realConfig.defaultSourceSet.manifestFile, globalScope),
//                        realConfig.buildType,
//                        realConfig.buildTypeSourceSet,
//                        VariantTypeImpl.LIBRARY,
//                        realConfig.signingConfig,
//                        globalScope.errorHandler,
//                        this::canParseManifest)
//    }

    override fun getRealScope(): VariantScope {
        return scope
    }

    override fun getTaskName(prefix: String, suffix: String): String {
        return scope.getTaskName("component${prefix.capitalize()}", suffix)
    }

    override fun getTaskName(prefix: String): String {
        return getTaskName(prefix, "")
    }

    override fun getGlobalScope(): GlobalScope {
        return globalScope
    }

    override fun getOutputScope(): OutputScope {
        return scope.outputScope
    }

    override fun getDirName(): String {
        return getVariantConfiguration().dirName
    }

    override fun getDirectorySegments(): MutableCollection<String> {
        return getVariantConfiguration().directorySegments
    }

    override fun getFullVariantName(): String {
        return getVariantConfiguration().fullName
    }

    private val taskContainer = PluginTaskContainer(scope.taskContainer)

    override fun getTaskContainer(): PluginTaskContainer {
        return taskContainer
    }


    override fun getFullName(): String {
        return getVariantConfiguration().fullName
    }

    override fun getVariantData(): BaseVariantData {
        return scope.variantData
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

    override fun getAarLibsDirectory(): File {
        return intermediate("packaged-classes", "libs")
    }

    //this output directly use AppPlugin's result
    override fun getMergeNativeLibsOutputDir(): File {
        return scope.mergeNativeLibsOutputDir
    }

    override fun getNdkSoFolder(): Collection<File> {
        return scope.ndkSoFolder
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
        return scope.variantConfiguration
//        return variantConfiguration
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

    override fun getProvidedOnlyClasspath(): FileCollection {
        val compile = getArtifactFileCollection(COMPILE_CLASSPATH, ALL, CLASSES)
        val pkg = getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, CLASSES)
        return compile.minus(pkg)
    }

    override fun getLocalPackagedJars(): FileCollection {
        return scope.localPackagedJars
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

    private fun getIntermediateDir(type: ComponentArtifactType): File {
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

    override fun consumesFeatureJars(): Boolean {
        return (getVariantData().type.isBaseModule
                && getVariantConfiguration().buildType.isMinifyEnabled
                && globalScope.hasDynamicFeatures())
    }

    override fun getNeedsMainDexListForBundle(): Boolean {
        return (getVariantData().type.isBaseModule
                && globalScope.hasDynamicFeatures()
                && getVariantConfiguration().dexingType.needsMainDexList)
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
        return FileUtils.join(getOutputsDir(), "aar")
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

    override fun getInternalArtifactTypeOutputFile(type: ComponentArtifactType, task: Task, fileName: String): File {
        return File(getIntermediateDir(type), "${task.name}/$fileName")
    }

    override fun getTransformManager(): TransformManager {
        return transformManager
    }

    override fun getComponentExtension(): ComponentExtension {
        return compExtension
    }

    private fun getGeneratedResourcesDir(name: String): File {
        return FileUtils.join(
                getGeneratedDir(),
                StringHelper.toStrings(
                        "res",
                        name,
                        getVariantConfiguration().directorySegments))
    }

    private fun getParser(file: File, globalScope: GlobalScope): ManifestAttributeSupplier {
        return (manifestParserMap).computeIfAbsent(file) { f ->
            DefaultManifestParser(
                    f,
                    this::canParseManifest,
                    globalScope.androidBuilder.issueReporter)
        }
    }

    private fun canParseManifest(): Boolean = true

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