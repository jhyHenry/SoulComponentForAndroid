package com.soul.reader.runalone.application;

import com.soul.component.basicres.BaseApplication;
import com.soul.component.componentlib.router.Router;

/**
 * Created by mrzhang on 2017/6/20.
 */

public class ReaderApplication extends BaseApplication {

    @Override
    public void onCreate() {
        super.onCreate();

        // 如果isRegisterCompoAuto为false，则需要通过反射加载组件
        Router.registerComponent("com.soul.share.applike.ShareApplike");
        Router.registerComponent("com.soul.share.kotlin.applike.KotlinApplike");
    }

}
