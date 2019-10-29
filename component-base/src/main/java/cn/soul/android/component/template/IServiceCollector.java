package cn.soul.android.component.template;

import java.util.List;

import cn.soul.android.component.IComponentService;

/**
 * @author panxinghai
 * <p>
 * date : 2019-10-29 22:34
 */
public interface IServiceCollector {
    List<IComponentService> gatherServices();
}
