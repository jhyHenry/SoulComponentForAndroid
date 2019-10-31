package cn.soul.android.component.combine;

import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.SzwarcfiterLauerSimpleCycles;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import cn.soul.android.component.Constants;
import cn.soul.android.component.exception.InitTaskExecuteException;

/**
 * @author panxinghai
 * <p>
 * date : 2019-10-14 23:01
 */
public class InitTaskManager {
    private static InitTaskManager sInstance;
    private Map<String, InitTask> mTasks;

    public static InitTaskManager instance() {
        if (sInstance == null) {
            synchronized (InitTaskManager.class) {
                if (sInstance == null) {
                    sInstance = new InitTaskManager();
                }
            }
        }
        return sInstance;
    }

    private InitTaskManager() {
        mTasks = new HashMap<>();

    }

    public InitTask findByName(String name) {
        return mTasks.get(name);
    }

    public void addInitTask(InitTask initTask) {
        String name = initTask.getName();
        if (name == null || name.equals("")) {
            return;
        }
        mTasks.put(initTask.getName(), initTask);
    }

    public List<InitTask> getExecuteTaskList() {
        List<InitTask> tasks = gatherTasks();
        if (tasks == null) {
            return new ArrayList<>();
        }
        for (InitTask task : tasks) {
            if (task.getName() == null || task.getName().equals("")) {
                continue;
            }
            mTasks.put(task.getName(), task);
        }
        return resolveTasksDependency();
    }

    private List<InitTask> gatherTasks() {
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

    private List<InitTask> resolveTasksDependency() throws InitTaskExecuteException {
        Graph<InitTask, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        for (InitTask task : mTasks.values()) {
            graph.addVertex(task);
            task.onConfigure();
            task.onDependency();
        }
        for (InitTask task : mTasks.values()) {
            for (InitTask depTask : task.getDependencyTasks()) {
                graph.addEdge(depTask, task);
            }
        }

        Iterator<InitTask> iterator = new TopologicalOrderIterator<>(graph);
        try {
            List<InitTask> result = new ArrayList<>();
            while (iterator.hasNext()) {
                result.add(iterator.next());
            }
            return result;
        } catch (Exception e) {
            SzwarcfiterLauerSimpleCycles<InitTask, DefaultEdge> simpleCycles = new SzwarcfiterLauerSimpleCycles<>(graph);
            List<List<InitTask>> cycles = simpleCycles.findSimpleCycles();
            StringBuilder sb = new StringBuilder("detect initTasks dependency cycle(s):\n");
            for (List<InitTask> cycle : cycles) {
                sb.append("\t");
                for (InitTask task : cycle) {
                    sb.append(task.getName()).append(" -> ");
                }
                sb.append(cycle.get(0).getName())
                        .append("\n");
            }
            sb.delete(sb.length() - 1, sb.length());
            throw new InitTaskExecuteException(sb.toString());
        }
    }
}
