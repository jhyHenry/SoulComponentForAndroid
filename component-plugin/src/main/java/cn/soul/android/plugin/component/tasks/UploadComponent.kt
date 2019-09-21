package cn.soul.android.plugin.component.tasks

import cn.soul.android.plugin.component.PluginVariantScope
import cn.soul.android.plugin.component.utils.Log
import com.android.build.gradle.internal.scope.TaskConfigAction
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.maven.MavenDeployer
import org.gradle.api.artifacts.maven.MavenResolver
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.publication.maven.internal.deployer.MavenRemoteRepository
import org.gradle.api.tasks.Upload
import java.io.File

/**
 * Created by nebula on 2019-07-21
 * 目前只支持本地文件夹的仓库
 */
//open class UploadComponent : Copy() {
//    class ConfigAction(private val componentName: String,
//                       private val versionName: String,
//                       private val scope: PluginVariantScope) : TaskConfigAction<UploadComponent> {
//        override fun getName(): String {
//            return scope.getTaskName("upload")
//        }
//
//        override fun getType(): Class<UploadComponent> {
//            return UploadComponent::class.java
//        }
//
//        override fun execute(task: UploadComponent) {
//            task.destinationDir =
//                    File("${scope.getComponentExtension().repoPath!!}/$componentName/$versionName/${scope.getFullName()}")
//            task.from(scope.getAarLocation())
//        }
//    }
//}
open class UploadComponent : Upload() {
//    @TaskAction
//    fun taskAction() {
//        val uploadArchives = project.tasks.withType(Upload::class.java).findByName("uploadArchives") as Upload
//    }

    class ConfigAction(private val componentName: String,
                       private val versionName: String,
                       private val scope: PluginVariantScope,
                       private val project: Project) : TaskConfigAction<UploadComponent> {
        override fun getName(): String {
            return scope.getTaskName("upload")
        }

        override fun getType(): Class<UploadComponent> {
            return UploadComponent::class.java
        }

        override fun execute(task: UploadComponent) {
//            task.destinationDir =
//                    File("${scope.getComponentExtension().repoPath!!}/$componentName/$versionName/${scope.getFullName()}")
//            task.from(scope.getAarLocation())
//            task.configuration =
//            Log.e(task.artifacts?.toString() + ":")
//            Log.e(scope.getAarLocation().name)
//            task.artifacts.plus(scope.getAarLocation())
            val config = this.project.configurations.getByName("archives")
            Log.e("upload taskName:${config.uploadTaskName}")
            createUploadTask(task, config.uploadTaskName, config, project)
            val mavenResolver = task.repositories.withType(MavenResolver::class.java)
            mavenResolver.forEach {
                Log.e(it.settings.toString())
            }
//            task.repositories.add(object : MavenDeployer{
//
//            })
            val uploadArchives = project.tasks.withType(Upload::class.java).findByName("uploadArchives")
            uploadArchives?.repositories?.forEach {
                Log.e("tag1", "add")
                task.repositories.add(it)
            }
            task.repositories.withType(MavenDeployer::class.java) {
                val remote = MavenRemoteRepository()
                remote.url = project.uri("../resultRepo/").toString()
                Log.e(remote.url)
                it.repository = remote
            }
//            task.configuration.allArtifacts.add(DefaultPublishArtifact())
//            task.configuration.allArtifacts.add()
//            task.artifacts.plus(File(scope.getAarLocation(), "component.aar"))
            task.artifacts.files.forEach {
                Log.e(it.absoluteFile.name)
            }

//            repository(url: uri('../resultRepo/'))
        }

        private fun createUploadTask(upload: Upload, name: String, configuration: Configuration, project: Project) {
            upload.description = "Uploads all artifacts belonging to $configuration"
//            upload.group = "upload"
            upload.configuration = configuration
            upload.isUploadDescriptor = true
            upload.conventionMapping.map("descriptorDestination") { File(project.buildDir, "ivy.xml") }
        }
//
//        private fun configureUploadArchivesTask() {
//            this.configurationActionContainer.add(Action<Project> { project ->
//                val uploadArchives = project.tasks.withType(Upload::class.java).findByName("uploadArchives")
//                if (uploadArchives != null) {
//                    val configuration = uploadArchives.configuration as ConfigurationInternal
//                    val module = configuration.module
//                    val `i$` = uploadArchives.repositories.withType(MavenResolver::class.java).iterator()
//
//                    while (`i$`.hasNext()) {
//                        val resolver = `i$`.next() as MavenResolver
//                        val pom = resolver.pom
//                        val publicationId = this@MavenPlugin.moduleIdentifierFactory.moduleWithVersion(if (pom.groupId == "unknown") module.group else pom.groupId, if (pom.artifactId == "empty-project") module.name else pom.artifactId, if (pom.version == "0") module.version else pom.version)
//                        this@MavenPlugin.publicationRegistry.registerPublication(project.path, DefaultProjectPublication(Describables.withTypeAndName("Maven repository", resolver.name), publicationId, true))
//                    }
//
//                }
//            })
//        }

    }
}