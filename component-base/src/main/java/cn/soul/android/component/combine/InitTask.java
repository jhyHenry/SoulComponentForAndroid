package cn.soul.android.component.combine;

/**
 * @author panxinghai
 * <p>
 * date : 2019-10-14 18:17
 */
public interface InitTask {
    void onDependency();

    void onConfigure();

    void onExecute();

    void dependsOn(Object... dep);

    Object[] getDependencies();

    InitTask[] getDependencyTasks();
}
