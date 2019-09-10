package cn.soul.android.component;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import cn.soul.android.component.node.RouterNode;
import cn.soul.android.component.template.IRouterFactory;
import cn.soul.android.component.template.IRouterLazyLoader;


/**
 * Created by nebula on 2019-07-20
 */
@SuppressLint("StaticFieldLeak")
public class SoulRouter {
    private volatile static SoulRouter sInstance;
    private volatile static boolean isInit = false;
    private static Context sContext;
    private IRouterLazyLoader mLazyLoader;

    private ConcurrentHashMap<String, RouterNode> mRouterTable;

    private SoulRouter() {
        mRouterTable = new ConcurrentHashMap<>();
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
        mRouterTable.put(node.getPath(), node);
    }

    public void navigate(int requestCode, Context context, RouterNode node) {
        if (context == null) {
            context = sContext;
        }
        if (node.getType() == RouterNode.ACTIVITY) {
            startActivity(requestCode, context, node);
        }
    }

    private void loadNode(String group) {
        if (mLazyLoader == null) {
            try {
                mLazyLoader = (IRouterLazyLoader) Class.forName(Constants.GEN_FILE_PACKAGE_NAME + "SoulRouterLazyLoaderImpl").newInstance();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            if (mLazyLoader == null) {
                return;
            }
        }
        List<IRouterFactory> list = mLazyLoader.lazyLoadFactoryByGroup(group);
        for (IRouterFactory factory : list) {
            factory.produceRouterNodes(this);
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
        return mRouterTable.get(path);
    }
}
