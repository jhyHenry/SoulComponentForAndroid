package cn.soul.android.plugin.component.tasks.transform

import com.android.build.api.transform.QualifiedContent
import com.android.build.gradle.internal.pipeline.TransformManager

/**
 * @author panxinghai
 *
 * date : 2019-11-18 16:28
 */
abstract class LibraryTraversalTransform : BaseTraversalTransform() {
    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return TransformManager.PROJECT_ONLY
    }
}