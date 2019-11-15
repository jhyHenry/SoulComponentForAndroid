package cn.soul.android.component.node;

import java.util.Set;

/**
 * @author panxinghai
 * <p>
 * date : 2019-11-15 16:20
 */
public enum NodeType {
    ACTIVITY(Collections.setOf("android.app.Activity")),
    FRAGMENT(Collections.setOf("androidx.fragment.app.Fragment",
            "android.app.Fragment",
            "android.support.v4.app.Fragment")),
    COMPONENT_SERVICE(Collections.setOf("cn.soul.android.component.IComponentService")),
    UNSPECIFIED(Collections.setOf(""));

    NodeType(Set<String> supportClasses) {
        this.supportClasses = supportClasses;
    }

    private Set<String> supportClasses;

    public Set<String> supportClasses() {
        return supportClasses;
    }
}
