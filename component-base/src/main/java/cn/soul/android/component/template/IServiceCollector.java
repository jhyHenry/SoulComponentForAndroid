package cn.soul.android.component.template;

import java.util.HashMap;

import cn.soul.android.component.IComponentService;

/**
 * collect all service, not only application but component gather by {@link IServiceProvider#getComponentServices()}
 *
 * @author panxinghai
 * <p>
 * date : 2019-10-29 22:34
 */
public interface IServiceCollector {
    HashMap<Class<? extends IComponentService>, ? extends IComponentService> gatherServices();

    HashMap<String, ? extends IComponentService> gatherAliasServices();
}
