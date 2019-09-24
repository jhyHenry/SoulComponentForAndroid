package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.plugin.component.manager.BuildType

/**
 * @author panxinghai
 *
 * date : 2019-09-24 17:39
 */
abstract class TypeTraversalTransform : BaseTraversalTransform() {
    protected var buildType = BuildType.APPLICATION

    fun setTaskBuildType(buildType: BuildType) {
        this.buildType = buildType
    }
}