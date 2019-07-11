package cn.soul.android.plugin.component

import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.BaseVariantData
import org.gradle.internal.impldep.org.junit.experimental.theories.internal.BooleanSupplier
import sun.jvm.hotspot.debugger.posix.elf.ELFFileParser.getParser
import java.io.File

/**
 * @author panxinghai
 *
 * date : 2019-07-11 15:33
 */
class PluginVariantScopeImpl(private val scope: VariantScope) : PluginVariantScope {
    private val taskContainer = PluginTaskContainer(scope.taskContainer)

    override fun getScope(): GlobalScope {
        return scope.globalScope
    }

    override fun getTaskContainer(): PluginTaskContainer {
        return taskContainer
    }

    override fun getTaskName(prefix: String, suffix: String): String {
        return scope.getTaskName("${prefix}Component", suffix)
    }

    override fun getVariantData(): BaseVariantData {
        return scope.variantData
    }

    override fun getGlobalScope(): GlobalScope {
        return scope.globalScope
    }

    override fun getVariantConfiguration(): GradleVariantConfiguration {
        val realConfig = scope.variantData.variantConfiguration
//        val variantConfig = GradleVariantConfiguration.getBuilderForExtension()
//            .create(
//                scope.globalScope.projectOptions,
//                defaultConfigData.getProductFlavor(),
//                sourceSet,
//                getParser(sourceSet.getManifestFile()),
//                buildTypeData.getBuildType(),
//                buildTypeData.getSourceSet(),
//                variantType,
//                signingOverride,
//                globalScope.getErrorHandler(),
//                false)
//        val configuration = GradleVariantConfiguration(
//            realConfig.project
//        )
        return scope.variantData.variantConfiguration
    }

    override fun getAidlSourceOutputDir(): File {
        return File(getGeneratedDir(), "source/aidl/${getVariantConfiguration().dirName}")
    }

    override fun getGeneratedDir(): File {
        return File(getScopeBuildDir(), "generated")
    }

    override fun getOutputsDir(): File {
        return File(getGeneratedDir(), "outputs")
    }

    override fun getScopeBuildDir(): File {
        return File(scope.globalScope.buildDir, "soul")
    }

    override fun getIntermediatesDir(): File {
        return File(scope.globalScope.buildDir, "soul/intermediates")
    }

    override fun getIncrementalDir(name: String): File {
        return File(getIntermediatesDir(), "incremental")
    }
}