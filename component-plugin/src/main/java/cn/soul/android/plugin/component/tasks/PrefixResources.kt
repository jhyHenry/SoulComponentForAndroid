package cn.soul.android.plugin.component.tasks

import cn.soul.android.plugin.component.exception.RGenerateException
import cn.soul.android.plugin.component.resolve.PrefixHelper
import cn.soul.android.plugin.component.utils.Log
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Created by nebula on 2019-08-15
 * xml 文件统一增加前缀 layout、xml等
 */
open class PrefixResources : AndroidVariantTask() {

    var packagedResFolder: File? = null
    var prefix: String = ""

    @TaskAction
    fun taskAction() {
        val folder = packagedResFolder ?: return
        val startTime = System.currentTimeMillis()
        PrefixHelper.instance.initWithPackagedRes(prefix, folder)
        folder.walk().filter { it.isFile && it.name != "values.xml" && it.name != "values-zh-rTW.xml" }
                .forEach {
                    PrefixHelper.instance.prefixResourceFile(it)
                }
        PrefixHelper.instance.prefixValues(File(folder, "values/values.xml"))
        PrefixHelper.instance.prefixValues(File(folder, "values-zh-rTW/values-zh-rTW.xml"))
        Log.i("prefix resources cost: ${System.currentTimeMillis() - startTime}ms")
    }

    class ConfigAction(private val scope: VariantScope, private val packagedResFolder: File, private val prefix: String) : VariantTaskCreationAction<PrefixResources>(scope) {
        override val type: Class<PrefixResources>
            get() = PrefixResources::class.java

        override val name: String
            get() = scope.getTaskName("prefix", "Resources")

        override fun configure(task: PrefixResources) {
            super.configure(task)
            task.variantName = scope.fullVariantName
            task.packagedResFolder = packagedResFolder
            task.prefix = prefix
        }
    }

}