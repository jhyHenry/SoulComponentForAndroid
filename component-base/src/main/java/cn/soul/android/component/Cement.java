package cn.soul.android.component;

import android.annotation.SuppressLint;

import java.util.HashMap;
import java.util.List;

import cn.soul.android.component.combine.InitTask;
import cn.soul.android.component.combine.InitTaskManager;

/**
 * @author panxinghai
 * <p>
 * date : 2019-10-14 18:07
 */
public class Cement {
    @SuppressLint("StaticFieldLeak")
    private volatile static Cement sInstance;
    private List<InitTask> mComponentTasks;
    private HashMap<String, IComponentService> mServiceMap;
    private InitTaskManager mTaskManager = new InitTaskManager();


    public static Cement instance() {
        if (sInstance == null) {
            synchronized (Cement.class) {
                if (sInstance == null) {
                    sInstance = new Cement();
                }
            }
        }
        return sInstance;
    }

    public void registerService(String alias, IComponentService service) {

    }

    public void registerService(IComponentService service) {

    }

    public <T extends IComponentService> T service(Class<T> clazz) {
        return null;
    }

    public IComponentService service(String alias) {
        return null;
    }

    public InitTaskManager getTaskManager() {
        return mTaskManager;
    }
}
