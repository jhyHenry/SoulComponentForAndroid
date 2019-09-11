package cn.soul.android.component;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;

import cn.soul.android.component.common.Trustee;
import cn.soul.android.component.node.RouterNode;


/**
 * Created by nebula on 2019-07-20
 */
@SuppressLint("StaticFieldLeak")
public class SoulRouter {
    private volatile static SoulRouter sInstance;
    private volatile static boolean isInit = false;
    private static Context sContext;

    private SoulRouter() {
    }

    public static void init(Application application) {
        if (isInit) {
            return;
        }
        isInit = true;
        sContext = application;
    }

    public static SoulRouter instance() {
        if (!isInit) {
            throw new RuntimeException("SoulRouter did't init");
        }
        if (sInstance == null) {
            synchronized (SoulRouter.class) {
                if (sInstance == null) {
                    sInstance = new SoulRouter();
                }
            }
        }
        return sInstance;
    }

    public void addRouterNode(RouterNode node) {
        Trustee.instance().putRouterNode(node);
    }

    public void navigate(int requestCode, Context context, RouterNode node) {
        if (context == null) {
            context = sContext;
        }
        if (node.getType() == RouterNode.ACTIVITY) {
            startActivity(requestCode, context, node);
        }
    }

    private void startActivity(int requestCode, Context context, RouterNode node) {
        Intent intent = new Intent(context, node.getTarget());
        if (!(context instanceof Activity)) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    public RouterNode route(String path) {
        return Trustee.instance().getRouterNode(path);
    }
}
