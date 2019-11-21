package cn.soul.android.plugin.component;

import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.LibraryPlugin;
import com.android.build.gradle.internal.VariantManager;
import com.android.builder.core.VariantType;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;

import java.util.List;
import java.util.Locale;

import cn.soul.android.plugin.component.extesion.ComponentExtension;
import cn.soul.android.plugin.component.tasks.transform.CementAppTransform;
import cn.soul.android.plugin.component.tasks.transform.CementLibTransform;
import cn.soul.android.plugin.component.utils.Descriptor;
import cn.soul.android.plugin.component.utils.Log;

/**
 * Created by nebula on 2019-11-21
 */
public class ComponentP implements Plugin<Project> {
    private ComponentExtension mPluginExtension;
    private Project project;
    private TaskManager taskManager;

    @Override
    public void apply(Project p) {
        project = p;
        Log.INSTANCE.p("apply component plugin. ");
        p.getPlugins().apply("maven");
        if (isRunForAar()) {
            p.getPlugins().apply("com.android.library");
            BaseExtension extension = project.getExtensions().findByType(BaseExtension.class);
            if (extension == null) {
                throw new RuntimeException();
            }
            extension.registerTransform(new CementLibTransform(project));
        } else {
            p.getPlugins().apply("com.android.application");
            BaseExtension extension = project.getExtensions().findByType(BaseExtension.class);
            if (extension == null) {
                throw new RuntimeException();
            }
            extension.registerTransform(new CementAppTransform(project));
        }
        mPluginExtension = project.getExtensions().create("component", ComponentExtension.class);
        taskManager = new TaskManager(p, mPluginExtension);
        project.afterEvaluate(project -> {
            mPluginExtension.ensureComponentExtension(project);
            configureProject();
            createTasks();
        });
    }

    private void configureProject() {
        Log.INSTANCE.p("configure project.");
        Gradle gradle = project.getGradle();
        List<String> taskNames = gradle.getStartParameter().getTaskNames();

        boolean needAddDependencies = needAddComponentDependencies(taskNames);

        mPluginExtension.getDependencies().appendDependencies(project, needAddDependencies);
        mPluginExtension.getDependencies().appendInterfaceApis(project, needAddDependencies);
    }

    private boolean isRunForAar() {
        Gradle gradle = project.getGradle();
        List<String> taskNames = gradle.getStartParameter().getTaskNames();
        if (taskNames.size() == 1) {
            String taskName = Descriptor.Companion.getTaskNameWithoutModule(taskNames.get(0));
            return taskName.startsWith("uploadComponent") ||
                    taskName.toLowerCase(Locale.getDefault()).startsWith("bundle") &&
                            taskName.toLowerCase(Locale.getDefault()).endsWith("aar");
        }
        return false;
    }

    private boolean needAddComponentDependencies(List<String> taskNames) {
        for (String it : taskNames) {
            String taskName = Descriptor.Companion.getTaskNameWithoutModule(it);
            if (taskName.startsWith("assemble") || taskName.startsWith("install")) {
                return true;
            }
        }
        return false;
    }

    private void createTasks() {
        Log.INSTANCE.p("create tasks.");
        if (isRunForAar()) {
            LibraryPlugin libPlugin = project.getPlugins().getPlugin(LibraryPlugin.class);
            VariantManager variantManager = libPlugin.getVariantManager();
            variantManager.getVariantScopes().forEach(it -> {
                VariantType variantType = it.getVariantData().getType();
                if (variantType.isTestComponent()) {
                    //这里是continue,不给test的variant创建task
                    return;
                }
                PluginTaskContainer taskContainer = new PluginTaskContainer();
                taskManager.setPluginTaskContainer(taskContainer);

                taskManager.createPrefixResourcesTask(it);

                taskManager.createGenerateSymbolTask(it);

                taskManager.createRefineManifestTask(it);

                taskManager.crateGenInterfaceArtifactTask(it);

                taskManager.createUploadTask(it);
            });
        } else {
//            val appPlugin = project.plugins.getPlugin(AppPlugin:: class.java)as BasePlugin<*>
//            val variantManager = appPlugin.variantManager
//            variantManager.variantScopes.forEach {
//
//                //                taskManager.createReplaceManifestTask(pluginVariantScope)
//            }
        }
    }
}
