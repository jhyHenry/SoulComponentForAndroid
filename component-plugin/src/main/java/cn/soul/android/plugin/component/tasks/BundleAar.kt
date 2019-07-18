package cn.soul.android.plugin.component.tasks

import cn.soul.android.plugin.component.ComponentArtifactType
import cn.soul.android.plugin.component.PluginVariantScope
import cn.soul.android.plugin.component.utils.Log
import com.android.SdkConstants
import com.android.build.api.artifact.ArtifactType
import com.android.build.gradle.AndroidConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantBuildArtifactsHolder
import com.android.builder.core.BuilderConstants
import org.gradle.api.Action
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.file.copy.SingleParentCopySpec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.bundling.Zip
import java.io.File
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Created by nebula on 2019-07-13
 */
@Suppress("UnstableApiUsage")
open class BundleAar : Zip() {
    private lateinit var archiveNameSupplier: () -> String
    @Input
    override fun getArchiveName() = archiveNameSupplier()

    override fun copy() {
        super.copy()
        if (Log.inScope(Log.Level.PROCESS)) {
            rootSpec.children.forEach { copySpecInternal ->
                printlnArtifactHolder(copySpecInternal)
            }
        }
    }

    private fun printlnArtifactHolder(copySpec: CopySpecInternal) {
        if (copySpec !is SingleParentCopySpec) {
            return
        }
        copySpec.sourcePaths.forEach { artifact ->
            if (artifact is File) {
                Log.d("others", artifact.absolutePath)

            } else {
                val clazz = artifact.javaClass.kotlin
                val typeField = clazz.memberProperties.find { it.name == "artifactType" }
                typeField?.isAccessible = true
                val type = typeField?.get(artifact) as ArtifactType

                val holderField = clazz.memberProperties.find { it.name == "artifacts" }
                holderField?.isAccessible = true
                val holder = holderField?.get(artifact) as VariantBuildArtifactsHolder
                holder.getFinalArtifactFiles(type).files.forEach {
                    Log.d(type.name(), it.absolutePath)
                }
            }
        }
        copySpec.children.forEach {
            printlnArtifactHolder(it)
        }
    }

    class ConfigAction(
            private val extension: AndroidConfig,
            private val variantScope: PluginVariantScope
    ) : TaskConfigAction<BundleAar> {

        override fun getName(): String = variantScope.getTaskName("bundle", "Aar")

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
//            extension.
            bundle.archiveNameSupplier = {
                "${variantScope.getComponentExtension().archiveName!!}-${variantScope.getFullName()}.aar"
            }
            bundle.extension = BuilderConstants.EXT_LIB_ARCHIVE

            //bundle format: name|type|path
            //bundle f:aidl|AIDL_PARCELABLE|

            //bundle consumer_proguard_file|intermediates/consumer_proguard_file/debug/mergeDebugConsumerProguardFiles/proguard.txt

            //bundle dataBinding|DATA_BINDING_ARTIFACT|

            //bundle data-binding-base-class-log|DATA_BINDING_BASE_CLASS_LOG_ARTIFACT|
            if (extension.dataBinding.isEnabled) {
                //todo:add dataBinding
            }
            //bundle AndroidManifest.xml|intermediates/merged_manifest/debug/processDebugManifest/merged/AndroidManifest.xml
            val libraryManifestFiles = artifacts.getFinalArtifactFiles(InternalArtifactType.LIBRARY_MANIFEST)
            bundle.from(libraryManifestFiles)

            //bundle R.txt|SYMBOL_LIST|intermediates/symbols/debug/R.txt
            val rDotTxt = artifacts.getFinalArtifactFiles(InternalArtifactType.SYMBOL_LIST)
            bundle.from(rDotTxt)

            //bundle f:res|PACKAGED_RES|intermediates/packaged_res/debug
            val packagedRes = variantScope.getIntermediateDir(InternalArtifactType.PACKAGED_RES)
            bundle.from(
                    packagedRes,
                    prependToCopyPath(SdkConstants.FD_RES)
            )
            //bundle f:rs|RENDERSCRIPT_HEADERS|intermediates/renderscript_headers/debug/packagedDebugRenderscript/out

            //bundle public.txt|PUBLIC_RES|intermediates/public_res/debug/packageDebugResources/public.txt

            //bundle COMPILE_ONLY_NAMESPACED_R_CLASS_JAR??
            //bundle RES_STATIC_LIBRARY


            //bundle f:jni|LIBRARY_AND_LOCAL_JARS_JNI|intermediates/library_and_local_jars_jni/debug

            //bundle lint.jar|LINT_JAR|

            //bundle annotations.zip|ANNOTATIONS_ZIP|intermediates/annotations_zip/debug/extractDebugAnnotations/annotations.zip

            //bundle class.jar|AAR_MAIN_JAR|intermediates/packaged-classes/debug/classes.jar
            val mainJar = artifacts.getFinalArtifactFiles(ComponentArtifactType.COMPONENT_AAR_MAIN_JAR)
            bundle.from(mainJar)

            //bundle f:libs|AAR_LIBS_DIRECTORY|intermediates/packaged-classes/debug/libs
            val aarLibs = artifacts.getFinalArtifactFiles(ComponentArtifactType.COMPONENT_AAR_LIBS_DIR)
            bundle.from(aarLibs)

            //bundle f:assets|LIBRARY_ASSETS|intermediates/library_assets/debug/packageDebugAssets/out

        }


        private fun prependToCopyPath(pathSegment: String) = Action { copySpec: CopySpec ->
            copySpec.eachFile { fileCopyDetails: FileCopyDetails ->
                fileCopyDetails.relativePath =
                        fileCopyDetails.relativePath.prepend(pathSegment)
            }
        }
    }
}