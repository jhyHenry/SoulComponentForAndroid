package cn.soul.android.plugin.component

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.SdkHandler
import com.android.build.gradle.internal.api.dsl.extensions.LibraryExtensionImpl
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.options.ProjectOptions
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import javax.inject.Inject

/**
 * Created by nebula on 2019-12-10
 */
open class CompLibPlugin @Inject constructor(registry: ToolingModelBuilderRegistry)
    : LibraryPlugin(registry) {
    override fun createExtension(project: Project?,
                                 projectOptions: ProjectOptions?,
                                 globalScope: GlobalScope?,
                                 sdkHandler: SdkHandler?,
                                 buildTypeContainer: NamedDomainObjectContainer<BuildType>?,
                                 productFlavorContainer: NamedDomainObjectContainer<ProductFlavor>?,
                                 signingConfigContainer: NamedDomainObjectContainer<SigningConfig>?,
                                 buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>?,
                                 sourceSetManager: SourceSetManager?,
                                 extraModelInfo: ExtraModelInfo?): BaseExtension {
        val libExtension = super.createExtension(project,
                projectOptions,
                globalScope,
                sdkHandler,
                buildTypeContainer,
                productFlavorContainer,
                signingConfigContainer,
                buildOutputs,
                sourceSetManager,
                extraModelInfo) as LibraryExtension
        project?.afterEvaluate {
            libExtension.apply {
                defaultConfig.applicationId = null
                buildTypes {
                    it.all { buildType ->
                        buildType.isShrinkResources = false
                    }
                }
            }

        }
        return libExtension
    }
}