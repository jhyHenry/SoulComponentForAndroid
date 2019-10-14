package cn.soul.android.component.combine;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;

import java.util.ArrayList;
import java.util.List;

import cn.soul.android.component.Cement;

/**
 * @author panxinghai
 * <p>
 * date : 2019-10-12 11:22
 */
@SuppressLint("NewApi")
abstract public class ComponentApplication extends Application
        implements InitTask {
    private Application mHost;
    private List<ComponentApplication> mComponentApplications;

    public void setHostApplication(Application application) {
        mHost = application;
    }

    public abstract void initAsApplication();

    public abstract void initAsComponent(Application realApplication);

    @Override
    public void onConfigure() {

    }

    @Override
    public void onDependency() {

    }

    @Override
    public void onExecute() {
        if (mHost == null) {
            initAsApplication();
        } else {
            initAsComponent(mHost);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (mHost != null) {
            return;
        }
        mComponentApplications = new ArrayList<>();
        List<InitTask> tasks = Cement.instance().getTaskManager().gatherTasks();
        for (InitTask task : tasks) {
            if (task instanceof ComponentApplication) {
                ComponentApplication componentApplication = (ComponentApplication) task;
                componentApplication.setHostApplication(this);
                mComponentApplications.add(componentApplication);
            }
            task.onExecute();
        }
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        for (ComponentApplication component : mComponentApplications) {
            component.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        for (ComponentApplication component : mComponentApplications) {
            component.onLowMemory();
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        for (ComponentApplication component : mComponentApplications) {
            component.onTerminate();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        for (ComponentApplication component : mComponentApplications) {
            component.onTrimMemory(level);
        }
    }

    @Override
    public void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }

    @Override
    public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        if (mHost != null) {
            mHost.registerActivityLifecycleCallbacks(callback);
            return;
        }
        super.registerActivityLifecycleCallbacks(callback);
    }

    @Override
    public void registerComponentCallbacks(ComponentCallbacks callback) {
        if (mHost != null) {
            mHost.registerComponentCallbacks(callback);
        }
        super.registerComponentCallbacks(callback);
    }

    @Override
    public void unregisterComponentCallbacks(ComponentCallbacks callback) {
        if (mHost != null) {
            mHost.unregisterComponentCallbacks(callback);
        }
        super.unregisterComponentCallbacks(callback);
    }

    @Override
    public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        if (mHost != null) {
            mHost.unregisterActivityLifecycleCallbacks(callback);
        }
        super.unregisterActivityLifecycleCallbacks(callback);
    }

    @Override
    public void registerOnProvideAssistDataListener(OnProvideAssistDataListener callback) {
        if (mHost != null) {
            mHost.registerOnProvideAssistDataListener(callback);
            return;
        }
        super.registerOnProvideAssistDataListener(callback);
    }

    @Override
    public void unregisterOnProvideAssistDataListener(OnProvideAssistDataListener callback) {
        if (mHost != null) {
            mHost.unregisterOnProvideAssistDataListener(callback);
            return;
        }
        super.unregisterOnProvideAssistDataListener(callback);
    }
}
