package cn.soul.android.plugin.component.exception

import org.gradle.api.GradleException

class RGenerateException(path: String)
    : GradleException("R generate is failed, please retry: [$path]:")