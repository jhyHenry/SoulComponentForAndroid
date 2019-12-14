package cn.soul.android.plugin.component.exception

/**
 * Created by nebula on 2019-12-11
 */
class RouterPathDuplicateException(path1: String, class1: String,
                                   path2: String, class2: String)
    : RuntimeException("Router path duplicate: [$path1]:$class1 - [$path2]:$class2")