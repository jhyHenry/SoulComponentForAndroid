package cn.soul.android.plugin.component

import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.*
import com.android.build.gradle.internal.variant.BaseVariantData
import org.gradle.api.Task
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileCollection
import java.io.File

/**
 * @author panxinghai
 *
 * date : 2019-07-11 15:32
 */
interface PluginVariantScope : TransformVariantScope {

    //transformVariantScope
    override fun getGlobalScope(): GlobalScope

    fun getTaskContainer(): PluginTaskContainer
    fun getFullName(): String
    fun getVariantData(): BaseVariantData
    fun getVariantConfiguration(): GradleVariantConfiguration
    fun getAidlSourceOutputDir(): File
    fun getAnnotationProcessorOutputDir(): File
    fun getBundleArtifactFolderForDataBinding(): File
    fun getAarClassesJar(): File
    fun getIntermediateJarOutputFolder(): File

    fun keepDefaultBootstrap(): Boolean

    fun getJava8LangSupportType(): VariantScope.Java8LangSupport

    fun getArtifactFileCollection(
            configType: AndroidArtifacts.ConsumedConfigType,
            artifactScope: AndroidArtifacts.ArtifactScope,
            artifactType: AndroidArtifacts.ArtifactType,
            attributeMap: Map<Attribute<String>, String>?): FileCollection

    fun getArtifactFileCollection(
            configType: AndroidArtifacts.ConsumedConfigType,
            artifactScope: AndroidArtifacts.ArtifactScope,
            artifactType: AndroidArtifacts.ArtifactType): FileCollection

    fun getArtifactCollection(
            configType: AndroidArtifacts.ConsumedConfigType,
            artifactScope: AndroidArtifacts.ArtifactScope,
            artifactType: AndroidArtifacts.ArtifactType): ArtifactCollection

    fun getBootClasspath(): FileCollection

    fun getJavaClasspath(configType: AndroidArtifacts.ConsumedConfigType,
                         classesType: AndroidArtifacts.ArtifactType): FileCollection

    fun getArtifacts(): BuildArtifactsHolder
    fun getDefaultMergeResourcesOutputDir(): File
    fun getIntermediateDir(type: InternalArtifactType): File
    fun getResourceBlameLogDir(): File
    fun getRenderscriptResOutputDir(): File
    fun getGeneratedResOutputDir(): File
    fun getGeneratedPngsOutputDir(): File
    fun getAarLocation(): File

    fun getJavaClasspathArtifacts(
            configType: AndroidArtifacts.ConsumedConfigType,
            classesType: AndroidArtifacts.ArtifactType,
            generatedBytecodeKey: Any?): ArtifactCollection

    fun isCrunchPngs(): Boolean
    fun useResourceShrinker(): Boolean

    fun getGeneratedDir(): File
    fun getOutputsDir(): File
    fun getIntermediatesDir(): File
    fun getScopeBuildDir(): File
    fun getIncrementalDir(name: String): File

    //custom
    fun getInternalArtifactTypeOutputFile(type: InternalArtifactType, task: Task, fileName: String): File

    fun getTransformManager(): TransformManager
}