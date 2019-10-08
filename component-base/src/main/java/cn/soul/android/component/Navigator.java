package cn.soul.android.component;

import android.content.Context;
import android.os.Bundle;

import java.io.Serializable;
import java.util.ArrayList;

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

    public Navigator withStringArray(String key, String[] value) {
        bundle.putStringArray(key, value);
        return this;
    }

    public Navigator withStringArrayList(String key, ArrayList<String> value) {
        bundle.putStringArrayList(key, value);
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

    public Navigator withBooleanArray(String key, boolean[] value) {
        bundle.putBooleanArray(key, value);
        return this;
    }

    public Navigator withFloat(String key, float value) {
        bundle.putFloat(key, value);
        return this;
    }

    public Navigator withFloatArray(String key, float[] value) {
        bundle.putFloatArray(key, value);
        return this;
    }

    public Navigator withLong(String key, long value) {
        bundle.putLong(key, value);
        return this;
    }

    public Navigator withLongArray(String key, long[] array) {
        bundle.putLongArray(key, array);
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
