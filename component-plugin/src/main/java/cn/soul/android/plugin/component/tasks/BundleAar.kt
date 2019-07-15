package cn.soul.android.plugin.component.tasks

import cn.soul.android.plugin.component.PluginVariantScope
import com.android.SdkConstants
import com.android.build.gradle.AndroidConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.builder.core.BuilderConstants
import org.gradle.api.Action
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.bundling.Zip

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
            bundle.archiveNameSupplier = { "debug.aar" }
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

            //bundle R.txt|SYMBOL_LIST|intermediates/symbols/debug/R.txt
            bundle.from(artifacts.getFinalArtifactFiles(InternalArtifactType.SYMBOL_LIST))

            //bundle f:res|PACKAGED_RES|intermediates/packaged_res/debug
            bundle.from(
                    variantScope.getIntermediateDir(InternalArtifactType.PACKAGED_RES),
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
            bundle.from()

            //bundle f:libs|AAR_LIBS_DIRECTORY|intermediates/packaged-classes/debug/libs

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