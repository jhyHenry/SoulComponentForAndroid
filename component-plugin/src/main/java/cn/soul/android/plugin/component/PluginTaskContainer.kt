package cn.soul.android.plugin.component

import cn.soul.android.plugin.component.tasks.*
import org.gradle.api.Task


/**
 * @author panxinghai
 *
 * date : 2019-07-11 15:26
 */
class PluginTaskContainer {
    var pluginRefineManifest: RefineManifest? = null
    var prefixResources: PrefixResources? = null
    var uploadTask: Task? = null
    var generateSymbol: GenerateSymbol? = null
    var genInterface: GenerateInterfaceArtifact? = null
}