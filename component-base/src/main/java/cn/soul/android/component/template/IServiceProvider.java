package cn.soul.android.component.template;

import java.util.HashMap;

import cn.soul.android.component.IComponentService;

/**
 * gather all service in component and provide them by implementation of this interface
 *
 * @author panxinghai
 * <p>
 * date : 2019-10-29 22:33
 */
public interface IServiceProvider {
    HashMap<Class<? extends IComponentService>, ? extends IComponentService> getComponentServices();

    HashMap<String, ? extends IComponentService> getAliasComponentServices();
}
