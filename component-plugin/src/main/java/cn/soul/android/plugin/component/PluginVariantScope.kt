package cn.soul.android.plugin.component

import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.variant.BaseVariantData
import java.io.File

/**
 * @author panxinghai
 *
 * date : 2019-07-11 15:32
 */
interface PluginVariantScope {
    fun getScope(): GlobalScope

    fun getTaskContainer(): PluginTaskContainer
    fun getTaskName(prefix: String, suffix: String): String
    fun getVariantData(): BaseVariantData
    fun getGlobalScope(): GlobalScope
    fun getVariantConfiguration(): GradleVariantConfiguration
    fun getAidlSourceOutputDir(): File

    fun getGeneratedDir(): File
    fun getOutputsDir(): File
    fun getIntermediatesDir(): File
    fun getScopeBuildDir(): File
    fun getIncrementalDir(name: String): File

}