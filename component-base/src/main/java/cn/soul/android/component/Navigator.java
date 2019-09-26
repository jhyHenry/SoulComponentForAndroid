package cn.soul.android.component;

import android.content.Context;

/**
 * @author panxinghai
 * <p>
 * date : 2019-09-25 18:33
 */
public class Navigator {
    String path;
    String group;

    public Navigator(String path) {
        this.path = path;
    }

    public Navigator(String path, String group) {
        this.path = path;
        this.group = group;
    }

    public void navigate() {
        navigate(null);
    }

    public void navigate(Context context) {
        navigate(0, context);
    }

    public void navigate(int requestCode, Context context) {
        SoulRouter.instance().navigate(requestCode, context, this);
    }
}
