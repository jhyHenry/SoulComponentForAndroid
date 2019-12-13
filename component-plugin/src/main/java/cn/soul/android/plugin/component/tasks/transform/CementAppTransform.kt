package cn.soul.android.plugin.component.tasks.transform

import com.google.common.collect.ImmutableSet
import org.gradle.api.Project

/**
 * @author panxinghai
 *
 * date : 2019-10-14 22:35
 */
class CementAppTransform(private val project: Project) : BaseActuatorSetTransform() {
    override fun getTransformActuatorSet(): Set<TransformActuator> {
        return ImmutableSet.of(
                RouterCompileActuator(project, false),
                InitTaskCompileActuator(project, false)
        )
    }


    override fun getName(): String {
        return "cementApp"
    }
}