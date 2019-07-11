package cn.soul.android.plugin.component.tasks
import cn.soul.android.plugin.component.PluginVariantScope
import com.android.annotations.NonNull
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * @author panxinghai
 *
 * date : 2019-07-11 16:42
 */
open class CheckManifest : AndroidVariantTask() {
    private var manifest: File? = null
    private var isOptional: Boolean? = null
    private var fakeOutputDir: File? = null

    @Optional
    @Input // we don't care about the content, just that the file is there.
    fun getManifest(): File? {
        return manifest
    }

    @Input // force rerunning the task if the manifest shows up or disappears.
    fun getManifestPresence(): Boolean {
        return manifest != null && manifest!!.isFile
    }

    fun setManifest(@NonNull manifest: File) {
        this.manifest = manifest
    }

    @Input
    fun getOptional(): Boolean? {
        return isOptional
    }

    fun setOptional(optional: Boolean?) {
        isOptional = optional
    }

    @OutputDirectory
    fun getFakeOutputDir(): File? {
        return fakeOutputDir
    }

    @TaskAction
    internal fun check() {
        if (!isOptional!! && manifest != null && !manifest!!.isFile) {
            throw IllegalArgumentException(
                "Main Manifest missing for variant $variantName. Expected path:${getManifest()?.absoluteFile}"
            )
        }
    }

    class ConfigAction(private val scope: PluginVariantScope, private val isManifestOptional: Boolean) :
        TaskConfigAction<CheckManifest> {
        override fun getName(): String {
            return scope.getTaskName("check", "Manifest")
        }

        override fun getType(): Class<CheckManifest> {
            return CheckManifest::class.java
        }

        override fun execute(task: CheckManifest) {
            scope.getTaskContainer().pluginCheckManifestTask = task
            task.variantName = scope.getVariantConfiguration().fullName
            task.setOptional(isManifestOptional)
            task.manifest = scope.getVariantConfiguration().mainManifest
            task.fakeOutputDir =
                File(scope.getIntermediatesDir(), "check-manifest/${scope.getVariantConfiguration().dirName}")
        }

    }
}