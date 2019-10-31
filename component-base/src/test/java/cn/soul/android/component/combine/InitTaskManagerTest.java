package cn.soul.android.component.combine;

import org.junit.Test;

import java.util.List;
import java.util.function.Consumer;

/**
 * @author panxinghai
 * <p>
 * date : 2019-10-31 16:27
 */
public class InitTaskManagerTest {
    @Test
    public void test() {
        InitTaskManager manager = InitTaskManager.instance();
        manager.addInitTask(new DefaultInitTask() {
            @Override
            public void onDependency() {
                super.onDependency();
                dependsOn("d");
            }

            @Override
            public String getName() {
                return "a";
            }
        });
        manager.addInitTask(new DefaultInitTask() {
            @Override
            public void onDependency() {
                super.onDependency();
                dependsOn("d");
                dependsOn("a");
            }

            @Override
            public String getName() {
                return "b";
            }
        });
        manager.addInitTask(new DefaultInitTask() {
            @Override
            public void onDependency() {
                dependsOn("e");
            }

            @Override
            public String getName() {
                return "c";
            }
        });
        manager.addInitTask(new DefaultInitTask() {
            @Override
            public void onDependency() {
                dependsOn("a");
            }

            @Override
            public String getName() {
                return "d";
            }
        });
        manager.addInitTask(new DefaultInitTask() {
            @Override
            public void onDependency() {
                dependsOn("b");
            }

            @Override
            public String getName() {
                return "e";
            }
        });
        List<InitTask> list = InitTaskManager.instance().getExecuteTaskList();
        if (list == null) {
            return;
        }
        list.forEach(new Consumer<InitTask>() {
            @Override
            public void accept(InitTask initTask) {
                System.out.print(initTask.getName() + "->");
            }
        });
    }
}
