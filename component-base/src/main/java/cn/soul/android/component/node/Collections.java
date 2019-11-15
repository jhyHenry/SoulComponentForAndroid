package cn.soul.android.component.node;

import java.util.HashSet;
import java.util.Set;

/**
 * @author panxinghai
 * <p>
 * date : 2019-11-15 16:27
 */
class Collections {
    static <T> Set<T> setOf(T... args) {
        Set<T> set = new HashSet<>();
        java.util.Collections.addAll(set, args);
        return set;
    }
}
