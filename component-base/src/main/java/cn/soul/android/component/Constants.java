package cn.soul.android.component;

import cn.soul.android.component.node.ActivityNode;
import cn.soul.android.component.node.FragmentNode;
import cn.soul.android.component.template.IInjectable;
import cn.soul.android.component.template.IRouterFactory;
import cn.soul.android.component.template.IRouterLazyLoader;

/**
 * Created by nebula on 2019-07-21
 */
public class Constants {
    public final static String FRAME_NAME = "cement";
    public final static String GEN_PACKAGE_NAME = "cn.soul.android." + FRAME_NAME + ".gen.";
    public final static String GEN_FOLDER_NAME = "cn/soul/android/" + FRAME_NAME + "/gen/";

    public final static String ROUTER_GEN_FILE_PACKAGE = GEN_PACKAGE_NAME + "router.";
    public final static String ROUTER_GEN_FILE_FOLDER = GEN_FOLDER_NAME + "router/";
    public final static String INIT_TASK_GEN_FILE_PACKAGE = GEN_PACKAGE_NAME + "task.";
    public final static String INIT_TASK_GEN_FILE_FOLDER = GEN_FOLDER_NAME + "task/";
    public final static String SERVICE_GEN_FILE_PACKAGE = GEN_PACKAGE_NAME + "service.";
    public final static String SERVICE_GEN_FILE_FOLDER = GEN_FOLDER_NAME + "service/";

    public final static String LAZY_LOADER_IMPL_NAME = "SoulRouterLazyLoaderImpl";
    public final static String INIT_TASK_COLLECTOR_IMPL_NAME = "InitTaskCollectorImpl";
    public final static String SERVICE_COLLECTOR_IMPL_NAME = "ServiceCollectorImpl";

    public final static String SOUL_ROUTER_CLASSNAME = SoulRouter.class.getName();
    public final static String ACTIVITY_NODE_CLASSNAME = ActivityNode.class.getName();
    public final static String FRAGMENT_NODE_CLASSNAME = FragmentNode.class.getName();
    public final static String ROUTER_LAZY_LOADER_CLASSNAME = IRouterLazyLoader.class.getName();
    public final static String ROUTER_FACTORY_CLASSNAME = IRouterFactory.class.getName();
    public final static String COMPONENT_SERVICE_CLASSNAME = IComponentService.class.getName();
    public final static String INJECTABLE_CLASSNAME = IInjectable.class.getName();
    public final static String REPLACE_META_NAME = "cement_replace_real_app";
    public final static String REPLACE_APPLICATION_NAME = "cn.soul.android.component.combine.CementApplication";

    public final static String COMPONENT_APPLICATION_NAME = "cn.soul.android.component.combine.ComponentApplication";
}
