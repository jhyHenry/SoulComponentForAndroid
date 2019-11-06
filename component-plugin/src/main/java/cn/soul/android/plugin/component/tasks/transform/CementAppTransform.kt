package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.component.Constants
import cn.soul.android.component.IComponentService
import cn.soul.android.component.annotation.ServiceInject
import cn.soul.android.component.annotation.TaskIgnore
import cn.soul.android.component.combine.IComponentTaskProvider
import cn.soul.android.component.combine.ITaskCollector
import cn.soul.android.component.combine.InitTask
import cn.soul.android.component.template.IServiceCollector
import cn.soul.android.component.template.IServiceProvider
import cn.soul.android.plugin.component.exception.ClassGenerateException
import cn.soul.android.plugin.component.extesion.ComponentExtension
import cn.soul.android.plugin.component.manager.BuildType
import cn.soul.android.plugin.component.resolve.ZipHelper
import cn.soul.android.plugin.component.utils.InjectHelper
import cn.soul.android.plugin.component.utils.Log
import cn.soul.android.plugin.component.utils.javassist.MethodGen
import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.Format
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import javassist.ClassClassPath
import javassist.CtClass
import org.gradle.api.Project
import java.io.File
import java.lang.reflect.Modifier

/**
 * @author panxinghai
 *
 * date : 2019-10-14 22:35
 */
class CementAppTransform(private val project: Project) : TypeTraversalTransform() {
    private lateinit var mInitTaskCtClass: CtClass
    private lateinit var mComponentServiceCtClass: CtClass

    private val mTaskClassList = arrayListOf<CtClass>()
    private val mTaskNameListProvider: (CtClass) -> Unit = {
        if (!Modifier.isAbstract(it.modifiers) && !it.hasAnnotation(TaskIgnore::class.java)) {
            mTaskClassList.add(it)
        }
    }

    private val mServiceAliasList = mutableMapOf<String, String>()
    private val mServiceClassList = mutableMapOf<String, String>()

    private val mComponentTaskProviderList = arrayListOf<String>()
    private val mComponentServiceProviderList = arrayListOf<String>()

    override fun preTransform(transformInvocation: TransformInvocation) {
        InjectHelper.instance.appendClassPath(ClassClassPath(InitTask::class.java))
        InjectHelper.instance.appendClassPath(ClassClassPath(IComponentService::class.java))
        InjectHelper.instance.appendClassPath(ClassClassPath(TaskIgnore::class.java))
        InjectHelper.instance.appendClassPath(ClassClassPath(ServiceInject::class.java))
        mInitTaskCtClass = InjectHelper.instance.getClassPool()[InitTask::class.java.name]
        mComponentServiceCtClass = InjectHelper.instance.getClassPool()[IComponentService::class.java.name]
    }

    override fun onDirVisited(dirInput: DirectoryInput, transformInvocation: TransformInvocation): Boolean {
        InjectHelper.instance.processFiles(dirInput.file)
                .nameFilter { file -> file.name.endsWith(".class") }
                .forEach {
                    //for task auto gather and inject
                    if (it.subtypeOf(mInitTaskCtClass)) {
                        mTaskNameListProvider.invoke(it)
                        return@forEach
                    }
                    if (it.hasAnnotation(ServiceInject::class.java)) {
                        if (it.isInterface || Modifier.isAbstract(it.modifiers) || !it.subtypeOf(mComponentServiceCtClass)) {
                            throw ClassGenerateException("ServiceInject must annotate non-abstract class which implement IComponentService")
                        }
                        val serviceInject = it.getAnnotation(ServiceInject::class.java) as ServiceInject
                        if (serviceInject.alias != "") {
                            mServiceAliasList[serviceInject.alias] = it.name
                        }
                        it.interfaces.forEach interfaceForEach@{ i ->
                            if (!i.subtypeOf(mComponentServiceCtClass)) {
                                return@interfaceForEach
                            }
                            mServiceClassList[i.name] = it.name
                        }
                    }
                }
        return false
    }

    override fun onJarVisited(jarInput: JarInput, transformInvocation: TransformInvocation): Boolean {
        if (buildType == BuildType.COMPONENT) {
            return false
        }
        ZipHelper.traversalZip(jarInput.file) {
            if (it.name.startsWith(Constants.INIT_TASK_GEN_FILE_FOLDER)) {
                Log.d("find task:${it.name}")
                mComponentTaskProviderList.add(getClassNameWithEntryName(it.name))
                return@traversalZip
            }
            if (it.name.startsWith(Constants.SERVICE_GEN_FILE_FOLDER)) {
                Log.d("find service:${it.name}")
                mComponentServiceProviderList.add(getClassNameWithEntryName(it.name))
                return@traversalZip
            }
        }
        return false
    }

    override fun postTransform(transformInvocation: TransformInvocation) {
        val dest = transformInvocation.outputProvider.getContentLocation(
                "injectGen",
                TransformManager.CONTENT_CLASS,
                TransformManager.SCOPE_FULL_PROJECT,
                Format.DIRECTORY)
        if (buildType == BuildType.COMPONENT) {
            if (mTaskClassList.isNotEmpty()) {
                genComponentTaskProviderImpl(dest)
            }
            if (mServiceAliasList.isNotEmpty() || mServiceClassList.isNotEmpty()) {
                genServiceProviderImpl(dest)
            }
            return
        }
        if (mTaskClassList.isNotEmpty() || mComponentTaskProviderList.isNotEmpty()) {
            genTaskCollectorImpl(dest)
        }
        if (mServiceClassList.isNotEmpty() || mServiceAliasList.isNotEmpty() || mComponentServiceProviderList.isNotEmpty()) {
            genServiceCollectorImpl(dest)
        }
    }

    /**
     * generate componentTask Provider implementation. see [IComponentTaskProvider].
     * @param dir target directory to save transform result
     */
    private fun genComponentTaskProviderImpl(dir: File) {
        MethodGen(Constants.INIT_TASK_GEN_FILE_PACKAGE + genComponentTaskProviderClassName())
                .signature(returnStatement = "public java.util.List",
                        name = "getComponentTasks")
                .interfaces(IComponentTaskProvider::class.java)
                .body { genGatherComponentTaskMethodBody() }
                .gen()?.writeFile(dir.absolutePath)
    }

    private fun genGatherComponentTaskMethodBody(): String {
        val sb = StringBuilder("{java.util.ArrayList list = new java.util.ArrayList();")
        mTaskClassList.forEach {
            if (isSubtypeOf(it, Constants.COMPONENT_APPLICATION_NAME)) {
                sb.append("${it.name} \$_ = new ${it.name}();")
                        .append("\$_.setNameByComponentName(\"${getComponentExtension().componentName}\");")
                        .append("list.add(\$_);")
            } else {
                sb.append("list.add(new ${it.name}());")
            }
        }
        sb.append("return list;}")
        return sb.toString()
    }

    /**
     * generate taskCollector implementation. see [ITaskCollector]
     * @param dir target directory to save transform result
     */
    private fun genTaskCollectorImpl(dir: File) {
        MethodGen(Constants.INIT_TASK_GEN_FILE_PACKAGE + Constants.INIT_TASK_COLLECTOR_IMPL_NAME)
                .signature(returnStatement = "public java.util.List",
                        name = "gatherTasks")
                .interfaces(ITaskCollector::class.java)
                .body { genGatherTaskMethodBody() }
                .gen()?.writeFile(dir.absolutePath)
    }

    private fun genGatherTaskMethodBody(): String {
        val sb = StringBuilder("{java.util.ArrayList list = new java.util.ArrayList();")
        mTaskClassList.forEach {
            if (isSubtypeOf(it, Constants.COMPONENT_APPLICATION_NAME)) {
                sb.append("${it.name} \$_ = new ${it.name}();")
                        .append("\$_.setNameByComponentName(\"${getComponentExtension().componentName}\");")
                        .append("list.add(\$_);")
            } else {
                sb.append("list.add(new ${it.name}());")
            }
        }
        mComponentTaskProviderList.forEach {
            sb.append("list.addAll(new $it().getComponentTasks());")
        }
        sb.append("return list;}")
        return sb.toString()
    }

    private fun isSubtypeOf(ctClass: CtClass, superType: String): Boolean {
        var ct = ctClass
        while (ct.superclass.name != "java.lang.Object") {
            if (ct.name == superType) {
                return true
            }
            ct = ct.superclass
        }
        return false
    }

    private fun genServiceProviderImpl(dir: File) {
        MethodGen(Constants.SERVICE_GEN_FILE_PACKAGE + genComponentServiceProviderClassName())
                .signature(returnStatement = "public java.util.HashMap",
                        name = "getComponentServices")
                .interfaces(IServiceProvider::class.java)
                .body {
                    val sb = StringBuilder("{java.util.HashMap result = new java.util.HashMap();")
                    mServiceClassList.forEach {
                        sb.append("result.put(${it.key}.class, new ${it.value}());")
                    }
                    sb.append("return result;}")
                    sb.toString()
                }
                .gen()
        MethodGen(Constants.SERVICE_GEN_FILE_PACKAGE + genComponentServiceProviderClassName())
                .signature(returnStatement = "public java.util.HashMap",
                        name = "getAliasComponentServices")
                .body {
                    val sb = StringBuilder("{java.util.HashMap result = new java.util.HashMap();")
                    mServiceAliasList.forEach {
                        sb.append("result.put(\"${it.key}\", new ${it.value}());")
                    }
                    sb.append("return result;}")
                    sb.toString()
                }
                .gen()?.writeFile(dir.absolutePath)
    }

    private fun genServiceCollectorImpl(dir: File) {
        MethodGen(Constants.SERVICE_GEN_FILE_PACKAGE + Constants.SERVICE_COLLECTOR_IMPL_NAME)
                .interfaces(IServiceCollector::class.java)
                .signature(returnStatement = "public java.util.HashMap",
                        name = "gatherServices")
                .body {
                    val sb = StringBuilder("{java.util.HashMap result = new java.util.HashMap();")
                    mServiceClassList.forEach {
                        sb.append("result.put(${it.key}.class, new ${it.value}());")
                    }
                    mComponentServiceProviderList.forEach {
                        sb.append("result.putAll(new $it().getComponentServices());")
                    }
                    sb.append("return result;}")
                    sb.toString()
                }
                .gen()
        MethodGen(Constants.SERVICE_GEN_FILE_PACKAGE + Constants.SERVICE_COLLECTOR_IMPL_NAME)
                .signature(returnStatement = "public java.util.HashMap",
                        name = "gatherAliasServices")
                .body {
                    val sb = StringBuilder("{java.util.HashMap result = new java.util.HashMap();")
                    mServiceAliasList.forEach {
                        sb.append("result.put(\"${it.key}\", new ${it.value}());")
                    }
                    mComponentServiceProviderList.forEach {
                        sb.append("result.putAll(new $it().getAliasComponentServices());")
                    }
                    sb.append("return result;}")
                    sb.toString()
                }
                .gen()?.writeFile(dir.absolutePath)
    }

    private fun getClassNameWithEntryName(name: String): String {
        return name.split('.').first().replace('/', '.')
    }

    private fun genComponentTaskProviderClassName(): String {
        val componentName = getComponentExtension().componentName
        return "$componentName\$\$TaskProvider"
    }

    private fun genComponentServiceProviderClassName(): String {
        val componentName = getComponentExtension().componentName
        return "$componentName\$\$ServiceProvider"
    }

    private fun getComponentExtension(): ComponentExtension {
        return project.extensions.findByType(ComponentExtension::class.java)
                ?: throw RuntimeException("can not find ComponentExtension, please check your build.gradle file")
    }

    override fun getName(): String {
        return "cementApp"
    }
}