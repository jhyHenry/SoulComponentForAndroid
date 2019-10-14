package cn.soul.android.component.node;

import android.os.Bundle;
import android.text.TextUtils;

/**
 * Created by nebula on 2019-07-21
 */
@SuppressWarnings("WeakerAccess")
public abstract class RouterNode {
    public static int ACTIVITY = 0x0;
    public static int SERVICE = 0x1;
    public static int FRAGMENT = 0x2;
    protected String mSchema;
    protected String mGroup;
    protected String mPath;
    protected int mType;
    protected Class<?> mTarget;
    protected Bundle mBundle;

    public RouterNode(String path, Class<?> target) {
        mPath = path;
        mTarget = target;
        mType = getType();
    }

    public RouterNode(String path, String group) {
        mPath = path;
        mGroup = group;
    }

    public abstract int getType();

    public String getPath() {
        return mPath;
    }

    public Class<?> getTarget() {
        return mTarget;
    }

    private boolean isPathValid() {
        if (TextUtils.isEmpty(mPath)) {
        }
        return false;
    }
}
