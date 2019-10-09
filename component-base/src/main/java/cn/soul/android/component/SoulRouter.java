package cn.soul.android.component;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import cn.soul.android.component.common.Trustee;
import cn.soul.android.component.node.RouterNode;
import cn.soul.android.component.template.IInjectable;


/**
 * Created by nebula on 2019-07-20
 */
@SuppressLint({"StaticFieldLeak"})
@SuppressWarnings({"unused", "WeakerAccess"})
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

    public void init(SoulRouterConfig config) {
        if (isInit) {
            return;
        }
        isInit = true;
        sContext = config.context;
        mNavigateCallback = config.navigateCallback;
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

    void navigate(int requestCode, Context context, Navigator guide, NavigateCallback callback) {
        RouterNode node = Trustee.instance().getRouterNode(guide.path);
        NavigateCallback navigateCallback = callback == null ? mNavigateCallback : callback;
        if (node == null) {
            if (navigateCallback != null) {
                navigateCallback.onLost(guide.path);
            }
            return;
        }
        if (navigateCallback != null) {
            navigateCallback.onFound(node);
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
