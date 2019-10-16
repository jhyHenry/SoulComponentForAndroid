package cn.soul.android.component.combine;

import java.util.List;

import cn.soul.android.component.Constants;

/**
 * @author panxinghai
 * <p>
 * date : 2019-10-14 23:01
 */
public class InitTaskManager implements ITaskCollector {
    public InitTaskManager() {
    }

    @Override
    public List<InitTask> gatherTasks() {
        try {
            Class<?> clazz = Class.forName(Constants.INIT_TASK_GEN_FILE_PACKAGE + Constants.INIT_TASK_COLLECTOR_IMPL_NAME);
            ITaskCollector collector = (ITaskCollector) clazz.newInstance();
            return collector.gatherTasks();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
        return null;
    }
}
