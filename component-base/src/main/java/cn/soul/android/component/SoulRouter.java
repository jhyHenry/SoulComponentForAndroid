package cn.soul.android.component;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;


import java.util.concurrent.ConcurrentHashMap;

import cn.soul.android.component.node.RouterNode;


/**
 * Created by nebula on 2019-07-20
 */
@SuppressLint("StaticFieldLeak")
public class SoulRouter {
    private volatile static SoulRouter sInstance;
    private volatile static boolean isInit = false;
    private static Context sContext;

    private ConcurrentHashMap<String, RouterNode> mRouterTable;

    private SoulRouter() {
        mRouterTable = new ConcurrentHashMap<>();
    }

    public static void init(Application application) {
        if (isInit) {
            return;
        }
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

    public void addRouterNode(String path, RouterNode node) {
        mRouterTable.put(path, node);
    }

    public void navigate() {

    }

    public RouterNode route(String path) {
        return mRouterTable.get(path);
    }
}
