package cn.soul.android.plugin.component.tasks.transform

import com.google.common.collect.ImmutableSet
import org.gradle.api.Project

/**
 * @author panxinghai
 *
 * date : 2019-10-14 22:35
 */
class KhalaAppTransform(private val project: Project) : BaseActuatorSetTransform() {
    override fun getTransformActuatorSet(): Set<TransformActuator> {
        return ImmutableSet.of(
                RouterCompileActuator(project, false)
                //需要代理初始化的话就把这句注释打开
//                , InitTaskCompileActuator(project, false)
        )
    }


    override fun getName(): String {
        return "khalaApp"
    }
}