package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.component.Constants
import cn.soul.android.component.annotation.Inject
import cn.soul.android.component.annotation.Router
import cn.soul.android.component.exception.InjectTypeException
import cn.soul.android.component.template.IRouterFactory
import cn.soul.android.component.template.IRouterLazyLoader
import cn.soul.android.plugin.component.extesion.ComponentExtension
import cn.soul.android.plugin.component.manager.BuildType
import cn.soul.android.plugin.component.manager.InjectType.*
import cn.soul.android.plugin.component.templete.IInjectable
import cn.soul.android.plugin.component.utils.InjectHelper
import cn.soul.android.plugin.component.utils.Log
import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.Format
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.pipeline.TransformManager
import javassist.ClassClassPath
import javassist.CtClass
import javassist.CtField
import javassist.CtMethod
import javassist.bytecode.FieldInfo
import javassist.bytecode.SignatureAttribute
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File
import java.util.zip.ZipFile

/**
 * All router relative code is in here.This class help inject code for Router Jump
 * @author panxinghai
 *
 * date : 2019-07-19 18:09
 */
class RouterCompileTransform(private val project: Project) : TypeTraversalTransform() {
    private val nodeMapByGroup = mutableMapOf<String, ArrayList<Pair<String, String>>>()
    private val groupMap = mutableMapOf<String, ArrayList<String>>()

    override fun preTraversal(transformInvocation: TransformInvocation) {
        super.preTraversal(transformInvocation)
        InjectHelper.instance.refresh()
        val extension = project.extensions.getByName("android") as AppExtension
        InjectHelper.instance.appendClassPath(File(extension.sdkDirectory, "platforms/${extension.compileSdkVersion}/android.jar").absolutePath)
        InjectHelper.instance.appendClassPath(ClassClassPath(IInjectable::class.java))
    }

    override fun onDirVisited(dirInput: DirectoryInput, transformInvocation: TransformInvocation): Boolean {
        //traversal all .class file and find the class which annotate by Router, record router path
        //and Class for RouterNode construction
        InjectHelper.instance.processFiles(dirInput.file)
                .nameFilter { file -> file.name.endsWith(".class") }
                .classFilter { ctClass ->
                    ctClass.getAnnotation(Router::class.java) != null
                }.forEach {
                    val routerAnnotation = it.getAnnotation(Router::class.java) as Router
                    val path = routerAnnotation.path
                    nodeMapByGroup.computeIfAbsent(getGroupWithPath(path, it.name)) {
                        arrayListOf()
                    }.add(Pair(path, it.name))
                    if (insertInjectImplement(it)) {
                        it.writeFile(dirInput.file.absolutePath)
                    }
                }
        return false
    }

    override fun onJarVisited(jarInput: JarInput, transformInvocation: TransformInvocation): Boolean {
        if (buildType == BuildType.APPLICATION) {
            val zip = ZipFile(jarInput.file)
            zip.use {
                it.entries().iterator().forEach { entry ->
                    if (entry.name.startsWith(Constants.GEN_FILE_PACKAGE_NAME_SPLIT_WITH_SLASH)) {
                        groupMap.computeIfAbsent(getGroupWithEntryName(entry.name)) {
                            arrayListOf()
                        }.add(getNameWithEntryName(entry.name))
                    }
                }
            }
        }
        return false
    }

    override fun postTransform(transformInvocation: TransformInvocation) {
        val dest = transformInvocation.outputProvider.getContentLocation(
                "routerGen",
                TransformManager.CONTENT_CLASS,
                TransformManager.PROJECT_ONLY,
                Format.DIRECTORY)

        if (nodeMapByGroup.isNotEmpty()) {
            nodeMapByGroup.forEach {
                genRouterFactoryImpl(dest, it.key, it.value)
            }
        }
        if (buildType == BuildType.APPLICATION) {
            nodeMapByGroup.forEach {
                groupMap.computeIfAbsent(it.key) {
                    arrayListOf()
                }.add(genRouterClassName(it.key))
            }
            InjectHelper.instance.getClassPool().appendClassPath(ClassClassPath(IRouterLazyLoader::class.java))
            InjectHelper.instance.getClassPool().appendClassPath(ClassClassPath(IRouterFactory::class.java))
            genRouterLazyLoaderImpl(dest, groupMap)
        }
    }

    /**
     *
     * @return if return true, this class need writeFile()
     */
    private fun insertInjectImplement(ctClass: CtClass): Boolean {
        val injectInfoList = arrayListOf<InjectInfo>()
        val classPool = InjectHelper.instance.getClassPool()
        ctClass.declaredFields.forEach {
            var inject = it.getAnnotation(Inject::class.java) ?: return@forEach
            println("field:" + it.name + ":" + it.type)
            inject = inject as Inject
            val annotationName = if (inject.name != "") {
                inject.name
            } else {
                it.name
            }
            injectInfoList.add(InjectInfo(it, annotationName, ctClass))
        }
        if (injectInfoList.size > 0) {
            try {
                val method = ctClass.getDeclaredMethod("autoSynthetic\$FieldInjectSoulComponent")
                method.setBody(produceInjectMethod(injectInfoList))
            } catch (e: Exception) {
                ctClass.addInterface(classPool[IInjectable::class.java.name])
                ctClass.addMethod(genAutoSyntheticInjectMethod(injectInfoList, ctClass))
            }
        }
        return injectInfoList.size > 0
    }

    private fun genAutoSyntheticInjectMethod(infoList: ArrayList<InjectInfo>, ctClass: CtClass): CtMethod {
        return CtMethod.make("public void autoSynthetic\$FieldInjectSoulComponent()${produceInjectMethod(infoList)}", ctClass)
    }

    private fun produceInjectMethod(infoList: ArrayList<InjectInfo>): String {
        val stringBuilder = StringBuilder("{")
        if (infoList.size > 0) {
            stringBuilder.append("if(!(\$0 instanceof ${IInjectable::class.java.name})){return;}")
            infoList.forEach {
                stringBuilder.append("${it.fieldName} = ")
                        .append(getPrefixStatement(it))
                        .append(getExtraStatement(it))
            }
        }
        stringBuilder.append("}")
        Log.e(stringBuilder.toString())
        return stringBuilder.toString()
    }

    private fun getPrefixStatement(injectInfo: InjectInfo): String {
        val type = injectInfo.type
        val classType = injectInfo.classType
        return when (values()[type]) {
            SERIALIZABLE, PARCELABLE_ARRAY -> "(${classType.name})getIntent()."
            else -> "getIntent()."
        }
    }

    private fun getExtraStatement(injectInfo: InjectInfo): String {
        val type = injectInfo.type
        val annotationName = injectInfo.annotationName
        val fieldName = injectInfo.fieldName
        return when (values()[type]) {
            INTEGER -> {
                "getIntExtra(\"$annotationName\", $fieldName);"
            }
            INT_ARRAY -> {
                "getIntArrayExtra(\"$annotationName\");"
            }
            INT_LIST -> {
                "getIntegerArrayListExtra(\"$annotationName\");"
            }
            BOOLEAN -> {
                "getBooleanExtra(\"$annotationName\", $fieldName);"
            }
            BOOLEAN_ARRAY -> {
                "getBooleanArrayExtra(\"$annotationName\");"
            }
            STRING -> {
                "getStringExtra(\"$annotationName\");"
            }
            STRING_ARRAY -> {
                "getStringArrayExtra(\"$annotationName\");"
            }
            STRING_LIST -> {
                "getStringArrayListExtra(\"$annotationName\");"
            }
            CHARACTER -> {
                "getCharExtra(\"$annotationName\", $fieldName);"
            }
            CHAR_ARRAY -> {
                "getCharArrayExtra(\"$annotationName\");"
            }
            SERIALIZABLE -> {
                "getSerializableExtra(\"$annotationName\");"
            }
            PARCELABLE -> {
                "getParcelableExtra(\"$annotationName\");"
            }
            PARCELABLE_ARRAY -> {
                "getParcelableArrayExtra(\"$annotationName\");"
            }
            PARCELABLE_LIST -> {
                "getParcelableArrayListExtra(\"$annotationName\");"
            }
            BYTE -> {
                "getByteExtra(\"$annotationName\", $fieldName);"
            }
            BYTE_ARRAY -> {
                "getByteArrayExtra(\"$annotationName\");"
            }
            DOUBLE -> {
                "getDoubleExtra(\"$annotationName\", $fieldName);"
            }
            DOUBLE_ARRAY -> {
                "getDoubleArrayExtra(\"$annotationName\");"
            }
            FLOAT -> {
                "getFloatExtra(\"$annotationName\", $fieldName);"
            }
            FLOAT_ARRAY -> {
                "getFloatArrayExtra(\"$annotationName\");"
            }
            LONG -> {
                "getLongExtra(\"$annotationName\", $fieldName);"
            }
            LONG_ARRAY -> {
                "getLongArrayExtra(\"$annotationName\");"
            }
            SHORT -> {
                "getShortExtra(\"$annotationName\", $fieldName);"
            }
            SHORT_ARRAY -> {
                "getShortArrayExtra(\"$annotationName\");"
            }
            CHAR_SEQUENCE -> {
                "getCharSequenceExtra(\"$annotationName\");"
            }
            CHAR_SEQUENCE_ARRAY -> {
                "getCharSequenceArrayExtra(\"$annotationName\");"
            }
            CHAR_SEQUENCE_LIST -> {
                "getCharSequenceArrayListExtra(\"$annotationName\");"
            }
        }
    }

    private fun genRouterFactoryImpl(dir: File, group: String, nodeList: List<Pair<String, String>>) {
        try {
            val classPool = InjectHelper.instance.getClassPool()
            val name = Constants.GEN_FILE_PACKAGE_NAME + genRouterClassName(group)
            var genClass: CtClass? = classPool.getOrNull(name)
            if (genClass == null) {
                genClass = classPool.makeClass(name)
                genClass.addInterface(classPool.get(IRouterFactory::class.java.name))
                genClass.addMethod(genProduceNodesMethod(genClass, nodeList))
            } else {
                if (genClass.isFrozen) {
                    genClass.defrost()
                }
                genClass.getDeclaredMethod("produceRouterNodes")
                        .setBody(produceNodesMethodBodySrc(nodeList))
            }
            genClass?.writeFile(dir.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            if (e.message != null) {
                Log.e(e.message!!)
            }
        }
    }

    private fun genProduceNodesMethod(declaringClass: CtClass, nodeList: List<Pair<String, String>>): CtMethod {
        return CtMethod.make(produceNodesMethodSrc(nodeList), declaringClass)
    }

    private fun produceNodesMethodSrc(nodeList: List<Pair<String, String>>): String {
        return "public java.util.List produceRouterNodes() " +
                produceNodesMethodBodySrc(nodeList)
    }

    private fun produceNodesMethodBodySrc(nodeList: List<Pair<String, String>>): String {
        val builder = StringBuilder("{")
                .append("java.util.ArrayList list = new java.util.ArrayList();")
        nodeList.forEach {
            builder.append("list.add(new ${Constants.ACTIVITY_NODE_CLASSNAME}(\"${it.first}\", ${it.second}.class));")
        }
        builder.append("return list;")
                .append("}")
        return builder.toString()
    }

    private fun genRouterLazyLoaderImpl(dir: File, groupMap: MutableMap<String, ArrayList<String>>) {
        try {
            val classPool = InjectHelper.instance.getClassPool()
            val name = Constants.GEN_FILE_PACKAGE_NAME + Constants.LAZY_LOADER_IMPL_NAME
            var genClass: CtClass? = classPool.getOrNull(name)
            if (genClass == null) {
                genClass = classPool.makeClass(name)
                genClass.addInterface(classPool.get(IRouterLazyLoader::class.java.name))
                genClass.addMethod(genLazyLoadFactoryByGroupMethod(groupMap, genClass))
            } else {
                if (genClass.isFrozen) {
                    genClass.defrost()
                }
                genClass.getDeclaredMethod("lazyLoadFactoryByGroup")
                        .setBody(produceLazyLoadFactoryBodySrc(groupMap))
            }
            genClass?.writeFile(dir.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            if (e.message != null) {
                Log.e(e.message!!)
            }
        }
    }

    private fun genLazyLoadFactoryByGroupMethod(groupMap: MutableMap<String, ArrayList<String>>, genClass: CtClass): CtMethod {
        return CtMethod.make(lazyLoadFactorySrc(groupMap), genClass)
    }

    private fun lazyLoadFactorySrc(groupMap: MutableMap<String, ArrayList<String>>): String {
        return "public java.util.List lazyLoadFactoryByGroup(String arg)" +
                produceLazyLoadFactoryBodySrc(groupMap)
    }

    private fun produceLazyLoadFactoryBodySrc(groupMap: MutableMap<String, ArrayList<String>>): String {
        val sb = StringBuilder("{")
                .append("java.util.ArrayList result =")
                .appendln("new java.util.ArrayList();")
        if (groupMap.isNotEmpty()) {
            sb.append("switch(\$1.hashCode()) {")
            groupMap.forEach {
                val group = it.key
                sb.append("case ${group.hashCode()}:{")
                it.value.forEach { name ->
                    sb.append("result.add(new ${Constants.GEN_FILE_PACKAGE_NAME}${name}());")
                }
                sb.append("return result;}")
            }
            sb.append("default:break;}")
        }
        sb.append("return result;}")
        return sb.toString()
    }

    private fun genRouterClassName(group: String): String {
        val componentName = getComponentExtension().componentName
        return "$group\$$componentName\$NodeFactory"
    }

    private fun getComponentExtension(): ComponentExtension {
        return project.extensions.findByType(ComponentExtension::class.java)
                ?: throw RuntimeException("can not find ComponentExtension, please check your build.gradle file")
    }

    private fun getGroupWithPath(path: String, clazz: String): String {
        val strings = path.split('/')
        if (strings.size < 3) {
            throw GradleException("invalid router path: \"$path\" in $clazz. Router Path must starts with '/' and has a group segment")
        }
        return strings[1]
    }

    private fun getGroupWithEntryName(name: String): String {
        return name.split('/').last().split('$')[0]
    }

    private fun getNameWithEntryName(name: String): String {
        return name.split('/').last().split('.')[0]
    }

    override fun getName(): String {
        return "routerCompile"
    }

    data class InjectInfo(val ctField: CtField,
                          val annotationName: String,
                          private val ctClass: CtClass) {
        var type: Int = 0
        val fieldName: String = ctField.name
        val classType: CtClass = ctField.type

        private val classPool = InjectHelper.instance.getClassPool()
        private val serializableType = classPool["java.io.Serializable"]
        private val parcelableType = classPool["android.os.Parcelable"]

        init {
            type = initType()
        }

        private fun initType(): Int {

            Log.e(classType.name)
            return when (classType.name) {
                "int", "java.lang.Integer" -> INTEGER.ordinal
                "boolean", "java.lang.Boolean" -> BOOLEAN.ordinal
                "java.lang.String" -> STRING.ordinal
                "char", "java.lang.Character" -> CHARACTER.ordinal
                "float", "java.lang.Float" -> FLOAT.ordinal
                "double", "java.lang.Double" -> DOUBLE.ordinal
                "byte", "java.lang.Byte" -> BYTE.ordinal
                "long", "java.lang.Long" -> LONG.ordinal
                "short", "java.lang.Short" -> SHORT.ordinal
                "java.lang.CharSequence" -> CHAR_SEQUENCE.ordinal

                "java.io.Serializable" -> SERIALIZABLE.ordinal
                "android.os.Parcelable" -> PARCELABLE.ordinal

                "int[]" -> INT_ARRAY.ordinal
                "boolean[]" -> BOOLEAN_ARRAY.ordinal
                "java.lang.String[]" -> STRING_ARRAY.ordinal
                "char[]" -> CHAR_ARRAY.ordinal
                "float[]" -> FLOAT_ARRAY.ordinal
                "double[]" -> DOUBLE_ARRAY.ordinal
                "byte[]" -> BYTE_ARRAY.ordinal
                "long[]" -> LONG_ARRAY.ordinal
                "short[]" -> SHORT_ARRAY.ordinal
                "java.lang.CharSequence[]" -> CHAR_SEQUENCE_ARRAY.ordinal


                "java.util.ArrayList" -> {
                    val fieldInfo = ctClass.classFile.fields.find { it is FieldInfo && it.name == fieldName } as FieldInfo
                    val sa = fieldInfo.getAttribute(SignatureAttribute.tag) as SignatureAttribute
                    val type = SignatureAttribute.toFieldSignature(sa.signature).jvmTypeName()
                    return when (val genericType = extractGeneric(type)) {
                        "java.lang.String" -> STRING_LIST.ordinal
                        "java.lang.Integer" -> INT_LIST.ordinal
                        "java.lang.CharSequence" -> CHAR_SEQUENCE_LIST.ordinal
                        else -> {
                            val ctClass = classPool[genericType]
                            if (ctClass.subtypeOf(parcelableType)) {
                                PARCELABLE_LIST.ordinal
                            } else {
                                throw InjectTypeException(ctClass.name, fieldName, type)
                            }
                        }
                    }
                }
                else -> {
                    println(classType.subtypeOf(serializableType))
                    when {
                        classType.isArray && classType.componentType.subtypeOf(parcelableType) -> PARCELABLE_ARRAY.ordinal
                        classType.subtypeOf(parcelableType) -> PARCELABLE.ordinal
                        classType.subtypeOf(serializableType) -> SERIALIZABLE.ordinal
                        else -> throw InjectTypeException(ctClass.name, fieldName, classType.name)
                    }
                }
            }
        }

        private fun extractGeneric(s: String): String {
            val startIndex = s.indexOf('<') + 1
            val endIndex = s.lastIndexOf('>')
            return s.substring(startIndex, endIndex).trim()
        }
    }
}