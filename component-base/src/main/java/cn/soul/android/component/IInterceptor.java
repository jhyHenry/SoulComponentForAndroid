package cn.soul.android.component;

import cn.soul.android.component.node.RouterNode;

/**
 * Created by nebula on 2020-02-13
 */
public interface IInterceptor {
    void intercept(Chain chain);

    interface Chain {
        RouterNode node();

        void proceed(RouterNode node);
    }
}
