package cn.soul.android.component.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author panxinghai
 * <p>
 * date : 2019-10-16 15:57
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface TaskInject {
}
