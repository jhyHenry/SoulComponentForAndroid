package cn.soul.android.component.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by nebula on 2019-09-28
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ComponentLauncher {
}
