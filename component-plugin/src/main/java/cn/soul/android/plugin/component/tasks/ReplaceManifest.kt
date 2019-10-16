package cn.soul.android.plugin.component.tasks

import cn.soul.android.component.Constants
import cn.soul.android.plugin.component.PluginVariantScope
import cn.soul.android.plugin.component.utils.AndroidXmlHelper
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileWriter

/**
 * @author panxinghai
 *
 * date : 2019-10-15 19:16
 */
open class ReplaceManifest : AndroidVariantTask() {
    var manifestFile: File? = null

    @TaskAction
    fun taskAction() {
        val reader = SAXReader()
        val document = reader.read(manifestFile)
        val root = document.rootElement

        val applicationElement = root.element(AndroidXmlHelper.TAG_APPLICATION)
        val attribute = applicationElement.attribute(AndroidXmlHelper.getAndroidQName("name"))

        val element = applicationElement.addElement("meta-data")
        element.addAttribute("android:name", Constants.REPLACE_META_NAME)
        element.addAttribute("android:value", attribute.value)

        attribute.value = Constants.REPLACE_APPLICATION_NAME
        FileWriter(manifestFile!!).use {
            XMLWriter(it).apply {
                write(root)
            }
        }

    }

    class ConfigAction(private val scope: PluginVariantScope,
                       private val manifestFile: File) : TaskConfigAction<ReplaceManifest> {
        override fun getName(): String {
            return scope.getTaskName("Replace", "Manifest")
        }

        override fun getType(): Class<ReplaceManifest> {
            return ReplaceManifest::class.java
        }

        override fun execute(task: ReplaceManifest) {
            task.manifestFile = manifestFile
            task.variantName = scope.getFullName()
        }
    }
}