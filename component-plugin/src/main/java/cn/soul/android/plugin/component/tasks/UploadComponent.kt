package cn.soul.android.plugin.component.tasks

import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Upload
import java.io.File

/**
 * Created by nebula on 2019-07-21
 *
 * reference [MavenPlugin]
 */
open class UploadComponent : Upload() {
    class ConfigAction(private val scope: VariantScope,
                       private val project: Project) : TaskConfigAction<UploadComponent> {
        override fun getName(): String {
            return "uploadComponent${scope.variantConfiguration.flavorName.capitalize()}"
        }

        override fun getType(): Class<UploadComponent> {
            return UploadComponent::class.java
        }

        override fun execute(task: UploadComponent) {
            val config = this.project.configurations.getByName("archives")
            createUploadTask(task, config.uploadTaskName, config, project)
            val uploadArchives = project.tasks.withType(Upload::class.java).findByName("uploadArchives")

            uploadArchives?.repositories?.forEach {
                task.repositories.add(it)
            }
//            task.repositories.withType(MavenDeployer::class.java) {
//                val remote = MavenRemoteRepository()
//                remote.url = project.uri("../resultRepo/").toString()
//                it.repository = remote
//                it.pom.apply {
//                    groupId = "com.test.component"
//                    artifactId = "test"
//                    version = "0.0.1"
//                }
//
//            }
        }

        private fun createUploadTask(upload: Upload, name: String, configuration: Configuration, project: Project) {
            upload.description = "Uploads all artifacts belonging to $configuration"
            upload.configuration = configuration
            upload.isUploadDescriptor = true
            upload.conventionMapping.map("descriptorDestination") { File(project.buildDir, "ivy.xml") }
        }
    }
}