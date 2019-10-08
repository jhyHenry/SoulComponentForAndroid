package cn.soul.android.component;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;

import cn.soul.android.component.common.Trustee;
import cn.soul.android.component.node.RouterNode;
import cn.soul.android.component.template.IInjectable;


/**
 * Created by nebula on 2019-07-20
 */
@SuppressLint("StaticFieldLeak")
@SuppressWarnings("unused")
public class SoulRouter {
    private volatile static SoulRouter sInstance;
    private volatile static boolean isInit = false;
    private static Context sContext;
    private NavigateCallback mNavigateCallback;

    private SoulRouter() {
    }

    public interface NavigateCallback {
        void onFound(RouterNode node);

        void onLost(String path);

        void onArrival(RouterNode node);
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

    public void inject(Object target) {
        if (target instanceof IInjectable) {
            ((IInjectable) target).autoSynthetic$FieldInjectSoulComponent();
        }
    }

    public Navigator route(String path) {
        return new Navigator(path);
    }

    public void addRouterNode(RouterNode node) {
        Trustee.instance().putRouterNode(node);
    }

    void navigate(int requestCode, Context context, Navigator guide) {
        RouterNode node = Trustee.instance().getRouterNode(guide.path);
        if (node == null) {
            if (mNavigateCallback != null) {
                mNavigateCallback.onLost(guide.path);
            }
            return;
        }
        if (mNavigateCallback != null) {
            mNavigateCallback.onFound(node);
        }
        if (context == null) {
            context = sContext;
        }
        if (node.getType() == RouterNode.ACTIVITY) {
            startActivity(requestCode, context, node, guide);
        }
    }

    private void startActivity(int requestCode, Context context, RouterNode node, Navigator guide) {
        Intent intent = new Intent(context, node.getTarget());
        intent.putExtras(guide.bundle);
        if (!(context instanceof Activity)) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

}
