package cn.soul.android.component.node;

/**
 * Created by nebula on 2019-07-21
 */
public class ActivityNode extends RouterNode {

    public ActivityNode(String path, Class<?> target) {
        super(path, target);
    }

    @Override
    public int getType() {
        return ACTIVITY;
    }
}
