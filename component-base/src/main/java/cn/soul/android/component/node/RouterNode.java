package cn.soul.android.component.node;

import android.content.Context;
import android.text.TextUtils;

import cn.soul.android.component.SoulRouter;

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

    public Class<?> getTarget() {
        return mTarget;
    }

    private boolean verifyPath() {
        if (TextUtils.isEmpty(mPath)) {
        }
        return false;
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
