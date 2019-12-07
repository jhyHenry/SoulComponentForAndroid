package cn.soul.android.component.node;

import java.util.Set;

import cn.soul.android.component.util.CollectionHelper;

/**
 * @author panxinghai
 * <p>
 * date : 2019-11-15 16:20
 */
public enum NodeType {
    ACTIVITY(CollectionHelper.setOf("android.app.Activity")),
    FRAGMENT(CollectionHelper.setOf("androidx.fragment.app.Fragment",
            "android.app.Fragment",
            "android.support.v4.app.Fragment")),
    COMPONENT_SERVICE(CollectionHelper.setOf("cn.soul.android.component.IComponentService")),
    UNSPECIFIED(CollectionHelper.setOf(""));

    NodeType(Set<String> supportClasses) {
        this.supportClasses = supportClasses;
    }

    private Set<String> supportClasses;

    public Set<String> supportClasses() {
        return supportClasses;
    }
}
