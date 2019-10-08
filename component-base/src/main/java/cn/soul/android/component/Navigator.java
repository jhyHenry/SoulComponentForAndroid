package cn.soul.android.component;

import android.content.Context;
import android.os.Bundle;

import java.io.Serializable;

/**
 * @author panxinghai
 * <p>
 * date : 2019-09-25 18:33
 */
public class Navigator {
    String path;
    String group;
    Bundle bundle;

    public Navigator(String path) {
        this.path = path;
        init();
    }

    public Navigator(String path, String group) {
        this.path = path;
        this.group = group;
        init();
    }

    private void init() {
        bundle = new Bundle();
    }

    public Navigator withString(String key, String value) {
        bundle.putString(key, value);
        return this;
    }

    public Navigator withSerializable(String key, Serializable serializable) {
        bundle.putSerializable(key, serializable);
        return this;
    }

    public Navigator withInt(String key, int value) {
        bundle.putInt(key, value);
        return this;
    }

    public Navigator withIntArray(String key, int[] array) {
        bundle.putIntArray(key, array);
        return this;
    }

    public Navigator withBoolean(String key, boolean value) {
        bundle.putBoolean(key, value);
        return this;
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
