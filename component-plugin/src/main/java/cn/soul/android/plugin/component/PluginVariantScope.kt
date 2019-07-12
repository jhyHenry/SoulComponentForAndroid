package cn.soul.android.plugin.component

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.variant.BaseVariantData
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileCollection
import java.io.File

/**
 * @author panxinghai
 *
 * date : 2019-07-11 15:32
 */
interface PluginVariantScope {
    fun getTaskContainer(): PluginTaskContainer
    fun getTaskName(prefix: String, suffix: String): String
    fun getVariantData(): BaseVariantData
    fun getGlobalScope(): GlobalScope
    fun getVariantConfiguration(): GradleVariantConfiguration
    fun getAidlSourceOutputDir(): File
    @NonNull
    fun getArtifactFileCollection(
            @NonNull configType: AndroidArtifacts.ConsumedConfigType,
            @NonNull artifactScope: AndroidArtifacts.ArtifactScope,
            @NonNull artifactType: AndroidArtifacts.ArtifactType,
            @Nullable attributeMap: Map<Attribute<String>, String>?): FileCollection

    @NonNull
    fun getArtifactFileCollection(
            @NonNull configType: AndroidArtifacts.ConsumedConfigType,
            @NonNull artifactScope: AndroidArtifacts.ArtifactScope,
            @NonNull artifactType: AndroidArtifacts.ArtifactType): FileCollection

    @NonNull
    fun getArtifactCollection(
            @NonNull configType: AndroidArtifacts.ConsumedConfigType,
            @NonNull artifactScope: AndroidArtifacts.ArtifactScope,
            @NonNull artifactType: AndroidArtifacts.ArtifactType): ArtifactCollection

    fun getArtifacts(): BuildArtifactsHolder
    fun getDefaultMergeResourcesOutputDir(): File
    fun getIntermediateDir(type: InternalArtifactType): File
    fun getResourceBlameLogDir():File
    fun getRenderscriptResOutputDir():File
    fun getGeneratedResOutputDir():File
    fun getGeneratedPngsOutputDir():File

    fun isCrunchPngs(): Boolean
    fun useResourceShrinker():Boolean

    fun getGeneratedDir(): File
    fun getOutputsDir(): File
    fun getIntermediatesDir(): File
    fun getScopeBuildDir(): File
    fun getIncrementalDir(name: String): File

}