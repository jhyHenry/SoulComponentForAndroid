package cn.soul.android.component;

import cn.soul.android.component.node.ActivityNode;
import cn.soul.android.component.template.IRouterFactory;
import cn.soul.android.component.template.IRouterLazyLoader;

/**
 * Created by nebula on 2019-07-21
 */
public class Constants {
    public final static String GEN_FILE_PACKAGE_NAME = "cn.soul.android.router.gen.";
    public final static String GEN_FILE_PACKAGE_NAME_SPLIT_WITH_SLASH = "cn/soul/android/router/gen/";
    public final static String LAZY_LOADER_IMPL_NAME = "SoulRouterLazyLoaderImpl";
    public final static String SOUL_ROUTER_CLASSNAME = SoulRouter.class.getName();
    public final static String ACTIVITY_NODE_CLASSNAME = ActivityNode.class.getName();
    public final static String ROUTER_LAZY_LOADER_CLASSNAME = IRouterLazyLoader.class.getName();
    public final static String ROUTER_FACTORY_CLASSNAME = IRouterFactory.class.getName();
    public final static String COMPONENT_SERVICE_CLASSNAME = ComponentService.class.getName();
}
