package com.soul.componentdemo.application;


import android.app.Application;
import android.util.Log;

import cn.soul.android.component.SoulRouter;
import cn.soul.android.component.SoulRouterConfig;
import cn.soul.android.component.node.RouterNode;

/**
 * Created by mrzhang on 2017/6/15.
 */

public class AppApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        SoulRouterConfig config = new SoulRouterConfig.Builder(this)
                .setNavigateCallback(new SoulRouter.NavigateCallback() {
                    @Override
                    public void onFound(RouterNode routerNode) {

                    }

                    @Override
                    public void onLost(String s) {
                        Log.e("router", "Lost:" + s);
                    }

                    @Override
                    public void onError(RouterNode routerNode, Exception e) {

                    }
                })
                .build();
        SoulRouter.init(config);

        //如果isRegisterCompoAuto为false，则需要通过反射加载组件
//        Router.registerComponent("com.soul.reader.applike.ReaderAppLike");
//        Router.registerComponent("com.soul.share.applike.ShareApplike");
    }

}
