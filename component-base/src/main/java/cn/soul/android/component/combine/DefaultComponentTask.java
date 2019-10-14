package cn.soul.android.component.combine;

/**
 * @author panxinghai
 * <p>
 * date : 2019-10-14 21:11
 */
public class DefaultComponentTask implements InitTask {
    @Override
    public void onDependency() {

    }

    @Override
    public void onConfigure() {

    }

    @Override
    public void onExecute() {

    }

    @Override
    public void dependsOn(Object... dep) {

    }

    @Override
    public Object[] getDependencies() {
        return new Object[0];
    }

    @Override
    public InitTask[] getDependencyTasks() {
        return new InitTask[0];
    }
}
