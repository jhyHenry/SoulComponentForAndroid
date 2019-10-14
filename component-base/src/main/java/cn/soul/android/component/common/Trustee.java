package cn.soul.android.component.common;

import android.util.SparseArray;

import java.util.List;

import cn.soul.android.component.Constants;
import cn.soul.android.component.exception.HashCollisionException;
import cn.soul.android.component.node.RouterNode;
import cn.soul.android.component.template.IRouterFactory;
import cn.soul.android.component.template.IRouterLazyLoader;

/**
 * @author panxinghai
 * <p>
 * date : 2019-09-11 15:34
 */
public class Trustee {
    private static volatile Trustee sInstance;

    private SparseArray<RouterTable> mRouterMapByGroup;
    private IRouterLazyLoader mLazyLoader;

    private Trustee() {
        mRouterMapByGroup = new SparseArray<>();
    }

    public static Trustee instance() {
        if (sInstance == null) {
            synchronized (Trustee.class) {
                if (sInstance == null) {
                    sInstance = new Trustee();
                }
            }
        }
        return sInstance;
    }

    public synchronized void putRouterNode(RouterNode node) {
        String group = getGroupByNode(node);
        RouterTable table = getRouterTable(group);
        table.putNode(node);
    }

    public synchronized RouterNode getRouterNode(String path) {
        String group = getGroupByPath(path);
        RouterTable table = getRouterTable(group);
        return table.getNode(path);
    }

    private RouterTable getRouterTable(String group) {
        //when first access, load RouterNodeFactory to get RouterNode
        RouterTable table = mRouterMapByGroup.get(group.hashCode());
        if (table == null) {
            table = new RouterTable();
            mRouterMapByGroup.put(group.hashCode(), table);
            loadNode(group, table);
        }
        return table;
    }

    private String getGroupByNode(RouterNode node) {
        String[] strings = node.getPath().split("/");
        if (strings.length < 3) {
            throw new IllegalArgumentException("invalid router path: \"" + node.getPath() + "\" in " + node.getTarget() + ". Router Path must starts with '/' and has a group segment");
        }
        return strings[1];
    }

    private String getGroupByPath(String path) {
        String[] strings = path.split("/");
        if (strings.length < 3) {
            throw new IllegalArgumentException("invalid router path: \"" + path + "\".");
        }
        return strings[1];
    }

    //lazy load node
    private synchronized void loadNode(String group, RouterTable table) {
        if (mLazyLoader == null) {
            try {
                mLazyLoader = (IRouterLazyLoader) Class.forName(Constants.GEN_FILE_PACKAGE_NAME + Constants.LAZY_LOADER_IMPL_NAME)
                        .newInstance();
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
            List<RouterNode> nodes = factory.produceRouterNodes();
            for (RouterNode node : nodes) {
                table.putNode(node);
            }
        }
    }

    private static class RouterTable {
        SparseArray<RouterNode> nodeMap;

        void putNode(RouterNode node) {
            if (nodeMap == null) {
                nodeMap = new SparseArray<>();
            }
            nodeMap.put(node.getPath().hashCode(), node);
        }

        RouterNode getNode(String path) {
            if (nodeMap == null) {
                return null;
            }
            RouterNode node = nodeMap.get(path.hashCode());
            if (node != null && !node.getPath().equals(path)) {
                throw new HashCollisionException(path, node.getPath());
            }
            return node;
        }
    }
}
