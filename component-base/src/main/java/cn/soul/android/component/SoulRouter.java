package cn.soul.android.component;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.fragment.app.Fragment;

import cn.soul.android.component.common.Trustee;
import cn.soul.android.component.node.FragmentNode;
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

        void onError(RouterNode node, Exception e);
    }

    public static void init(SoulRouterConfig config) {
        if (isInit) {
            return;
        }
        isInit = true;
        if (config == null) {
            throw new IllegalArgumentException("SoulRouterConfig cannot be null");
        }
        sContext = config.context;
        instance().mNavigateCallback = config.navigateCallback;
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

    public static void inject(Object target) {
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

    Object navigate(int requestCode, Context context, Navigator guide, NavigateCallback callback) {
        RouterNode node = Trustee.instance().getRouterNode(guide.path);
        NavigateCallback navigateCallback = callback == null ? mNavigateCallback : callback;
        if (node == null) {
            if (navigateCallback != null) {
                navigateCallback.onLost(guide.path);
            }
            return null;
        }
        if (navigateCallback != null) {
            navigateCallback.onFound(node);
        }
        if (context == null) {
            context = sContext;
        }
        switch (node.getType()) {
            case ACTIVITY:
                startActivity(requestCode, context, node, guide);
                break;
            case FRAGMENT:
                try {
                    return getFragmentInstance((FragmentNode) node, guide);
                } catch (InstantiationException e) {
                    if (callback != null) {
                        callback.onError(node, e);
                    }
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    if (callback != null) {
                        callback.onError(node, e);
                    }
                    e.printStackTrace();
                }
                return null;
            case COMPONENT_SERVICE:
                break;
            default:
                break;
        }
        return null;
    }

    private Fragment getFragmentInstance(FragmentNode routerNode, Navigator guide) throws InstantiationException, IllegalAccessException {
        Fragment fragment = (Fragment) routerNode.getTarget().newInstance();
        fragment.setArguments(guide.bundle);
        return fragment;
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
