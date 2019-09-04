package cn.soul.android.component.template;

/**
 * @author panxinghai
 * <p>
 * date : 2019-09-04 15:56
 */
public interface IRouterLazyLoader {
    IRouterFactory lazyLoadFactoryByGroup(String group);
}
