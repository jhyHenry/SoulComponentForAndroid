package cn.soul.android.component.node;

import android.text.TextUtils;

/**
 * Created by nebula on 2019-07-21
 */
public abstract class RouterNode {
    public static int ACTIVITY = 0;
    public static int SERVICE = 1;
    protected String mPath;
    protected int mType;
    protected Class<?> mTarget;

    public RouterNode(String path, Class<?> target) {
        mPath = path;
        mTarget = target;
    }

    public abstract int getType();

    private boolean verifyPath() {
        if (TextUtils.isEmpty(mPath)) {
        }
        return false;
    }
}
