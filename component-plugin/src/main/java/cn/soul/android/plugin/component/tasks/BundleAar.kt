package cn.soul.android.plugin.component.tasks

import android.databinding.tool.DataBindingBuilder
import cn.soul.android.plugin.component.PluginVariantScope
import com.android.SdkConstants
import com.android.build.gradle.AndroidConfig
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.builder.core.BuilderConstants
import org.gradle.api.Action
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.file.copy.SingleParentCopySpec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.bundling.Zip
import java.io.File
import java.lang.StringBuilder

/**
 * Created by nebula on 2019-07-13
 */
open class BundleAar : Zip() {
    private lateinit var archiveNameSupplier: () -> String
    @Input
    override fun getArchiveName() = archiveNameSupplier()

    class ConfigAction(
            private val extension: AndroidConfig,
            private val variantScope: PluginVariantScope
    ) : TaskConfigAction<BundleAar> {

        override fun getName() = variantScope.getTaskName("bundle", "Aar")

        override fun getType() = BundleAar::class.java

        override fun execute(bundle: BundleAar) {
            val artifacts = variantScope.getArtifacts()

            // Sanity check, there should never be duplicates.
            bundle.duplicatesStrategy = DuplicatesStrategy.FAIL
            // Make the AAR reproducible. Note that we package several zips inside the AAR, so all of
            // those need to be reproducible too before we can switch this on.
            // https://issuetracker.google.com/67597902
            bundle.isReproducibleFileOrder = true
            bundle.isPreserveFileTimestamps = false

            bundle.description = ("Assembles a bundle containing the library in "
                    + variantScope.getVariantConfiguration().fullName
                    + ".")

            bundle.destinationDir = variantScope.getAarLocation()
            bundle.archiveNameSupplier = { variantScope.getOutputScope().mainSplit.outputFileName }
            bundle.extension = BuilderConstants.EXT_LIB_ARCHIVE
            bundle.from(
                    variantScope.getArtifacts().getArtifactFiles(
                            InternalArtifactType.AIDL_PARCELABLE
                    ),
                    prependToCopyPath(SdkConstants.FD_AIDL)
            )

            bundle.from(artifacts.getFinalArtifactFiles(InternalArtifactType.CONSUMER_PROGUARD_FILE))
            if (extension.dataBinding.isEnabled) {
                bundle.from(
                        variantScope.getGlobalScope().project.provider {
                            variantScope.getArtifacts().getFinalArtifactFiles(
                                    InternalArtifactType.DATA_BINDING_ARTIFACT)
                        },
                        prependToCopyPath(DataBindingBuilder.DATA_BINDING_ROOT_FOLDER_IN_AAR)
                )
                bundle.from(
                        variantScope.getGlobalScope().project.provider {
                            variantScope.getArtifacts().getFinalArtifactFiles(
                                    InternalArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT
                            )
                        },
                        prependToCopyPath(
                                DataBindingBuilder.DATA_BINDING_CLASS_LOG_ROOT_FOLDER_IN_AAR
                        )
                )
            }
            bundle.from(artifacts.getFinalArtifactFiles(InternalArtifactType.LIBRARY_MANIFEST))
            // TODO: this should be unconditional b/69358522
            if (java.lang.Boolean.TRUE != variantScope.getGlobalScope().extension.aaptOptions.namespaced) {
                bundle.from(artifacts.getFinalArtifactFiles(InternalArtifactType.SYMBOL_LIST))
                bundle.from(
                        artifacts.getFinalArtifactFiles(InternalArtifactType.PACKAGED_RES),
                        prependToCopyPath(SdkConstants.FD_RES)
                )
            }
            bundle.from(
                    artifacts.getFinalArtifactFiles(InternalArtifactType.RENDERSCRIPT_HEADERS),
                    prependToCopyPath(SdkConstants.FD_RENDERSCRIPT)
            )
            bundle.from(artifacts.getFinalArtifactFiles(InternalArtifactType.PUBLIC_RES))
            if (artifacts.hasArtifact(InternalArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR)) {
                bundle.from(artifacts.getFinalArtifactFiles(
                        InternalArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR))
            }
            if (artifacts.hasArtifact(InternalArtifactType.RES_STATIC_LIBRARY)) {
                bundle.from(artifacts.getFinalArtifactFiles(InternalArtifactType.RES_STATIC_LIBRARY))
            }
            bundle.from(
                    artifacts.getFinalArtifactFiles(InternalArtifactType.LIBRARY_AND_LOCAL_JARS_JNI),
                    prependToCopyPath(SdkConstants.FD_JNI)
            )
            bundle.from(variantScope.getGlobalScope().artifacts
                    .getFinalArtifactFiles(InternalArtifactType.LINT_JAR))
            if (artifacts.hasArtifact(InternalArtifactType.ANNOTATIONS_ZIP)) {
                bundle.from(artifacts.getFinalArtifactFiles(InternalArtifactType.ANNOTATIONS_ZIP))
            }
            bundle.from(artifacts.getFinalArtifactFiles(InternalArtifactType.AAR_MAIN_JAR))
            bundle.from(
                    artifacts.getFinalArtifactFiles(InternalArtifactType.AAR_LIBS_DIRECTORY),
                    prependToCopyPath(SdkConstants.LIBS_FOLDER)
            )
            bundle.from(
                    variantScope.getArtifacts()
                            .getFinalArtifactFiles(InternalArtifactType.LIBRARY_ASSETS),
                    prependToCopyPath(SdkConstants.FD_ASSETS))

            variantScope.getArtifacts().appendArtifact(
                    InternalArtifactType.AAR,
                    listOf(File(variantScope.getAarLocation(),
                            variantScope.getOutputScope().mainSplit.outputFileName)),
                    bundle)

            bundle.mainSpec.children.forEach {
                val spec = it as SingleParentCopySpec
//                spec.sourcePaths.forEach {path->
//                    val artifact = path as BuildArtifactsHolder.BuildableArtifactData
//                    println("main:$artifact")
//                }
                val child = spec.children
                var tab = "\t"
                child.forEach { cspc ->
                    val childSpec = cspc as SingleParentCopySpec
                    childSpec.sourcePaths.forEach { sp ->
                        val csp = sp as BuildableArtifactImpl
                        println(filesToString(tab, csp.fileCollection))
                    }
                    childSpec.children.forEach { ccspc ->
                        tab += "\t"
                    }
                }
            }

            variantScope.getTaskContainer().pluginBundleAarTask = bundle
        }

        private fun filesToString(tab: String, fc: FileCollection): String {
            val sb = StringBuilder()
            fc.files.forEach {
                sb.append(tab)
                        .append(it.absolutePath)
                        .append("\n")
            }
            return sb.toString()
        }

        private fun prependToCopyPath(pathSegment: String) = Action { copySpec: CopySpec ->
            copySpec.eachFile { fileCopyDetails: FileCopyDetails ->
                fileCopyDetails.relativePath =
                        fileCopyDetails.relativePath.prepend(pathSegment)
            }
        }
    }
}