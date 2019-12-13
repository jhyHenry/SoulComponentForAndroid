package cn.soul.android.plugin.component.tasks

import cn.soul.android.component.Constants
import cn.soul.android.plugin.component.utils.AndroidXmlHelper
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
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
                ?: return

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

    class ConfigAction(private val scope: VariantScope,
                       private val manifestFile: File) :
            VariantTaskCreationAction<ReplaceManifest>(scope) {
        override val name: String
            get() = scope.getTaskName("Replace", "Manifest")


        override val type: Class<ReplaceManifest>
            get() = ReplaceManifest::class.java

        override fun configure(task: ReplaceManifest) {
            task.manifestFile = manifestFile
            task.variantName = scope.fullVariantName
        }
    }
}