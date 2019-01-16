package com.soul.share.kotlin.applike

import com.soul.component.componentlib.applicationlike.IApplicationLike
import com.soul.component.componentlib.router.ui.UIRouter

/**
 * Created by mrzhang on 2018/1/3.
 */
class KotlinApplike : IApplicationLike {

    val uiRouter = UIRouter.getInstance()

    override fun onCreate() {
        uiRouter.registerUI("kotlin")
    }

    override fun onStop() {
        uiRouter.unregisterUI("kotlin")
    }
}