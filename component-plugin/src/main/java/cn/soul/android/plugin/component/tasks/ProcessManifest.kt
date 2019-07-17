package cn.soul.android.plugin.component.tasks

import cn.soul.android.plugin.component.PluginVariantScope
import com.android.SdkConstants
import com.android.build.gradle.internal.core.VariantConfiguration
import com.android.build.gradle.internal.dsl.CoreBuildType
import com.android.build.gradle.internal.dsl.CoreProductFlavor
import com.android.build.gradle.internal.scope.*
import com.android.build.gradle.internal.tasks.TaskInputHelper
import com.android.build.gradle.tasks.ManifestProcessorTask
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.MergingReport
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.apache.tools.ant.BuildException
import org.gradle.api.tasks.*
import java.io.File
import java.io.IOException
import java.util.function.Supplier

/**
 * @author panxinghai
 *
 * date : 2019-07-17 15:34
 */
open class ProcessManifest : ManifestProcessorTask() {
    private var minSdkVersion: Supplier<String>? = null
    private var targetSdkVersion: Supplier<String>? = null
    private var maxSdkVersion: Supplier<Int>? = null

    private var variantConfiguration: VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor>? = null
    private var outputScope: OutputScope? = null

    private var processorManifestOutputFile: File? = null

    override fun doFullTaskAction() {
        val aaptFriendlyManifestOutputFile = aaptFriendlyManifestOutputFile
        val mergingReport = builder
                .mergeManifestsForApplication(
                        getMainManifest(),
                        getManifestOverlays(),
                        emptyList(),
                        getNavigationFiles(), null,
                        getPackageOverride(),
                        getVersionCode(),
                        getVersionName(),
                        getMinSdkVersion(),
                        getTargetSdkVersion(),
                        getMaxSdkVersion(),
                        processorManifestOutputFile!!.absolutePath,
                        aaptFriendlyManifestOutputFile.absolutePath, null,
                        ManifestMerger2.MergeType.LIBRARY,
                        variantConfiguration!!.manifestPlaceholders,
                        emptyList(),
                        reportFile)/* outInstantRunManifestLocation */

        val mergedXmlDocument = mergingReport.getMergedXmlDocument(MergingReport.MergedManifestKind.MERGED)

        val properties = if (mergedXmlDocument != null)
            ImmutableMap.of(
                    "packageId", mergedXmlDocument.packageName,
                    "split", mergedXmlDocument.splitName)
        else
            ImmutableMap.of()

        try {
            BuildOutput(
                    InternalArtifactType.MERGED_MANIFESTS,
                    outputScope!!.mainSplit,
                    processorManifestOutputFile!!,
                    properties)
                    .save(manifestOutputDirectory)

            BuildOutput(
                    InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS,
                    outputScope!!.mainSplit,
                    aaptFriendlyManifestOutputFile,
                    properties)
                    .save(aaptFriendlyManifestOutputDirectory)
        } catch (e: IOException) {
            throw BuildException("Exception while saving build metadata : ", e)
        }

    }

    @Internal
    override fun getAaptFriendlyManifestOutputFile(): File {
        Preconditions.checkNotNull(outputScope!!.mainSplit)
        return FileUtils.join(
                aaptFriendlyManifestOutputDirectory,
                outputScope!!.mainSplit.dirName,
                SdkConstants.ANDROID_MANIFEST_XML)
    }

    @Input
    @Optional
    fun getMinSdkVersion(): String {
        return minSdkVersion!!.get()
    }

    @Input
    @Optional
    fun getTargetSdkVersion(): String {
        return targetSdkVersion!!.get()
    }

    @Input
    @Optional
    fun getMaxSdkVersion(): Int? {
        return maxSdkVersion!!.get()
    }

    @Internal
    fun getVariantConfiguration(): VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor>? {
        return variantConfiguration
    }

    fun setVariantConfiguration(
            variantConfiguration: VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor>) {
        this.variantConfiguration = variantConfiguration
    }

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getMainManifest(): File {
        return variantConfiguration!!.mainManifest
    }

    @Input
    @Optional
    fun getPackageOverride(): String {
        return variantConfiguration!!.applicationId
    }

    @Input
    fun getVersionCode(): Int {
        return variantConfiguration!!.versionCode
    }

    @Input
    @Optional
    fun getVersionName(): String {
        return variantConfiguration!!.versionName
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getManifestOverlays(): List<File> {
        return variantConfiguration!!.manifestOverlays
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getNavigationFiles(): List<File> {
        return variantConfiguration!!.navigationFiles
    }

    /**
     * Returns a serialized version of our map of key value pairs for placeholder substitution.
     *
     * This serialized form is only used by gradle to compare past and present tasks to determine
     * whether a task need to be re-run or not.
     */
    @Input
    @Optional
    fun getManifestPlaceholders(): String {
        return ManifestProcessorTask.serializeMap(variantConfiguration!!.manifestPlaceholders)
    }

    @Input
    fun getMainSplitFullName(): String {
        // This information is written to the build output's metadata file, so it needs to be
        // annotated as @Input
        return outputScope!!.mainSplit.fullName
    }

    class ConfigAction(private val scope: PluginVariantScope) : TaskConfigAction<ProcessManifest> {

        override fun getName(): String {
            return scope.getTaskName("process", "Manifest")
        }

        override fun getType(): Class<ProcessManifest> {
            return ProcessManifest::class.java
        }

        override fun execute(processManifest: ProcessManifest) {
            val config = scope.getVariantConfiguration()
            val androidBuilder = scope.globalScope.androidBuilder

            processManifest.setAndroidBuilder(androidBuilder)
            processManifest.variantName = config.fullName

            processManifest.variantConfiguration = config

            val mergedFlavor = config.mergedFlavor

            processManifest.minSdkVersion = TaskInputHelper.memoize {
                val minSdkVersion1 = mergedFlavor.minSdkVersion ?: return@memoize null
                minSdkVersion1.apiString
            }

            processManifest.targetSdkVersion = TaskInputHelper.memoize {
                val targetSdkVersion = mergedFlavor.targetSdkVersion ?: return@memoize null
                targetSdkVersion.apiString
            }

            processManifest.maxSdkVersion = TaskInputHelper.memoize { mergedFlavor.maxSdkVersion }

            processManifest.manifestOutputDirectory = scope.getArtifacts()
                    .appendArtifact(
                            InternalArtifactType.MERGED_MANIFESTS,
                            processManifest,
                            "merged")

            processManifest.aaptFriendlyManifestOutputDirectory = scope.getArtifacts()
                    .appendArtifact(
                            InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS,
                            processManifest,
                            "aapt")

            processManifest.processorManifestOutputFile = File(
                    processManifest.manifestOutputDirectory,
                    SdkConstants.FN_ANDROID_MANIFEST_XML)

            scope.getArtifacts()
                    .appendArtifact(
                            InternalArtifactType.LIBRARY_MANIFEST,
                            ImmutableList.of(processManifest.processorManifestOutputFile!!),
                            processManifest)

            processManifest.outputScope = scope.outputScope

            val reportFile = FileUtils.join(
                    scope.globalScope.outputsDir,
                    "logs",
                    "manifest-merger-"
                            + config.baseName
                            + "-report.txt")

            processManifest.reportFile = reportFile
            scope.getArtifacts()
                    .appendArtifact(
                            InternalArtifactType.MANIFEST_MERGE_REPORT,
                            ImmutableList.of(reportFile),
                            processManifest)

        }
    }
}