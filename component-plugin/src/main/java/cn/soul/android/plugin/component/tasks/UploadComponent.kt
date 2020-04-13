package cn.soul.android.plugin.component.tasks

import cn.soul.android.plugin.component.utils.Log
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.maven.MavenDeployer
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.plugins.MavenPluginConvention
import org.gradle.api.publication.maven.internal.DefaultDeployerFactory
import org.gradle.api.publication.maven.internal.MavenFactory
import org.gradle.api.publication.maven.internal.deployer.MavenRemoteRepository
import org.gradle.api.tasks.Upload
import org.gradle.internal.Factory
import org.gradle.internal.logging.LoggingManagerInternal
import java.io.File

/**
 * Created by nebula on 2019-07-21
 *
 * reference [MavenPlugin]
 */
open class UploadComponent : Upload() {
    class ConfigAction(private val scope: VariantScope,
                       private val project: Project) :
            TaskCreationAction<UploadComponent>() {
        override val name: String
            get() = "uploadComponent${scope.variantConfiguration.flavorName.capitalize()}"


        override val type: Class<UploadComponent>
            get() = UploadComponent::class.java

        override fun configure(task: UploadComponent) {
            val config = this.project.configurations.getByName("archives")
            createUploadTask(task, config.uploadTaskName, config, project)
            val uploadArchives = project.tasks.withType(Upload::class.java).findByName("uploadArchives")

            val projectInternal = project as ProjectInternal
//            val instantiator = projectInternal.services.get(Instantiator::class.java) as Instantiator
//            val baseRepositoryFactory = projectInternal.services.get(BaseRepositoryFactory::class.java) as BaseRepositoryFactory
//            val callbackDecorator = projectInternal.services.get(CollectionCallbackActionDecorator::class.java) as CollectionCallbackActionDecorator
//            val resHandler = instantiator.newInstance(DefaultRepositoryHandler::class.java, *arrayOf(baseRepositoryFactory, instantiator, callbackDecorator)) as RepositoryHandler


            val mavenFactory = projectInternal.services.get(MavenFactory::class.java) as MavenFactory
            val mavenConvention = MavenPluginConvention(project, mavenFactory)
            val convention = project.getConvention()
            convention.plugins["maven"] = mavenConvention
            val pluginConvention = mavenConvention

            val pluginInstance = project.plugins.getPlugin(MavenPlugin::class.java)



            val loggingManagerFactory = getField(pluginInstance, "loggingManagerFactory") as Factory<LoggingManagerInternal>
            val fileResolver = getField(pluginInstance, "fileResolver") as FileResolver
            val mavenSettingsProvider = getField(pluginInstance, "mavenSettingsProvider") as MavenSettingsProvider
            val mavenRepositoryLocator = getField(pluginInstance, "mavenRepositoryLocator") as LocalMavenRepositoryLocator

            val deployerFactory = DefaultDeployerFactory(mavenFactory, loggingManagerFactory, fileResolver, pluginConvention, project.getConfigurations(), pluginConvention.getConf2ScopeMappings(), mavenSettingsProvider, mavenRepositoryLocator)

            val repo = deployerFactory.createMavenDeployer()
            repo.name = "componentRepo"
            task.repositories.add(repo)

            task.repositories.withType(MavenDeployer::class.java) {
                val remote = MavenRemoteRepository()
                remote.url = project.uri("../componentRepo/").toString()
                it.repository = remote
                it.pom.apply {
                    groupId = "com.test.component"
                    artifactId = "test"
                    version = "0.0.1"
                }

            }
        }

        private fun createUploadTask(upload: Upload, name: String, configuration: Configuration, project: Project) {
            upload.description = "Uploads all artifacts belonging to $configuration"
            upload.configuration = configuration
            upload.isUploadDescriptor = true
            upload.conventionMapping.map("descriptorDestination") { File(project.buildDir, "ivy.xml") }
        }

        private fun getField(instance: Any, fieldName: String): Any {
            val field = instance.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            return field.get(instance)
        }
    }
}