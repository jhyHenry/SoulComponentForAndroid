package cn.soul.android.component.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author panxinghai
 * <p>
 * date : 2019-09-27 11:51
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface Inject {
    String name() default "";

    boolean require() default false;
}
