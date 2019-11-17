package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.component.Constants
import cn.soul.android.component.annotation.Inject
import cn.soul.android.component.annotation.Router
import cn.soul.android.component.exception.InjectTypeException
import cn.soul.android.component.node.NodeType
import cn.soul.android.component.template.IRouterLazyLoader
import cn.soul.android.component.template.IRouterNodeProvider
import cn.soul.android.plugin.component.extesion.ComponentExtension
import cn.soul.android.plugin.component.manager.BuildType
import cn.soul.android.plugin.component.manager.InjectType.*
import cn.soul.android.plugin.component.resolve.ZipHelper
import cn.soul.android.plugin.component.utils.InjectHelper
import cn.soul.android.plugin.component.utils.Log
import cn.soul.android.plugin.component.utils.javassist.MethodGen
import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.Format
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import javassist.*
import javassist.bytecode.FieldInfo
import javassist.bytecode.SignatureAttribute
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File
import java.util.*

/**
 * All router relative code is in here.This class help inject code for Router Jump
 * @author panxinghai
 *
 * date : 2019-07-19 18:09
 */
class RouterCompileTransform(private val project: Project) : TypeTraversalTransform() {
    private val nodeMapByGroup = mutableMapOf<String, ArrayList<NodeInfo>>()
    private val groupMap = mutableMapOf<String, ArrayList<String>>()

    override fun preTraversal(transformInvocation: TransformInvocation) {
        super.preTraversal(transformInvocation)
        InjectHelper.instance.refresh()
        InjectHelper.instance.appendAndroidPlatformPath(project)
    }

    override fun onDirVisited(dirInput: DirectoryInput, transformInvocation: TransformInvocation): Boolean {
        //traversal all .class file and find the class which annotate by Router, record router path
        //and Class for RouterNode construction
        InjectHelper.instance.processFiles(dirInput.file)
                .nameFilter { file -> file.name.endsWith(".class") }
                .classFilter { ctClass ->
                    ctClass.hasAnnotation(Router::class.java)
                }.forEach {
                    val routerAnnotation = it.getAnnotation(Router::class.java) as Router
                    val path = routerAnnotation.path
                    val nodeInfo = NodeInfo(path, it)
                    nodeMapByGroup.computeIfAbsent(getGroupWithPath(path, it.name)) {
                        arrayListOf()
                    }.add(nodeInfo)
                    if (insertInjectImplement(nodeInfo)) {
                        it.writeFile(dirInput.file.absolutePath)
                    }
                }
        return false
    }

    override fun onJarVisited(jarInput: JarInput, transformInvocation: TransformInvocation): Boolean {
        if (buildType == BuildType.COMPONENT) {
            return false
        }
        ZipHelper.traversalZip(jarInput.file) { entry ->
            if (entry.name.startsWith(Constants.ROUTER_GEN_FILE_FOLDER)) {
                groupMap.computeIfAbsent(getGroupWithEntryName(entry.name)) {
                    arrayListOf()
                }.add(getNameWithEntryName(entry.name))
                groupMap.forEach {
                    Log.d("${it.key} : ${Arrays.toString(it.value.toArray())}")
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
                genRouterProviderImpl(dest, it.key, it.value)
            }
        }
        if (buildType == BuildType.APPLICATION) {
            nodeMapByGroup.forEach {
                groupMap.computeIfAbsent(it.key) {
                    arrayListOf()
                }.add(genRouterProviderClassName(it.key))
            }
            InjectHelper.instance.getClassPool().appendClassPath(ClassClassPath(IRouterLazyLoader::class.java))
            InjectHelper.instance.getClassPool().appendClassPath(ClassClassPath(IRouterNodeProvider::class.java))
            Log.d("generate lazy loader")
            genRouterLazyLoaderImpl(dest, groupMap)
        }
    }

    /**
     *
     * @return if return true, this class need writeFile()
     */
    private fun insertInjectImplement(nodeInfo: NodeInfo): Boolean {
        val injectInfoList = arrayListOf<InjectInfo>()
        val classPool = InjectHelper.instance.getClassPool()
        if (nodeInfo.type != NodeType.ACTIVITY && nodeInfo.type != NodeType.FRAGMENT) {
            //one inject for activity and fragment
            return false
        }

        val ctClass = nodeInfo.ctClass
        ctClass.declaredFields.forEach {
            var inject = it.getAnnotation(Inject::class.java) ?: return@forEach
            if (Modifier.isFinal(it.modifiers)) {
                Log.e("skip field ${ctClass.simpleName}.${it.name} inject: cannot inject value for final field.")
                return@forEach
            }
            inject = inject as Inject
            val annotationName = if (inject.name != "") {
                inject.name
            } else {
                it.name
            }
            injectInfoList.add(InjectInfo(it, annotationName, ctClass))
        }
        if (injectInfoList.size > 0) {
            val isFragment = nodeInfo.type == NodeType.FRAGMENT
            try {
                val method = ctClass.getDeclaredMethod("autoSynthetic\$FieldInjectSoulComponent")
                method.setBody(produceInjectMethod(isFragment, injectInfoList))
            } catch (e: Exception) {
                ctClass.addInterface(classPool[Constants.INJECTABLE_CLASSNAME])
                ctClass.addMethod(genAutoSyntheticInjectMethod(isFragment, injectInfoList, ctClass))
            }
        }
        return injectInfoList.size > 0
    }

    private fun genAutoSyntheticInjectMethod(isFragment: Boolean, infoList: ArrayList<InjectInfo>, ctClass: CtClass): CtMethod {
        return CtMethod.make("public void autoSynthetic\$FieldInjectSoulComponent()${produceInjectMethod(isFragment, infoList)}", ctClass)
    }

    private fun produceInjectMethod(isFragment: Boolean, infoList: ArrayList<InjectInfo>): String {
        val stringBuilder = StringBuilder("{")
        if (infoList.size > 0) {
            stringBuilder.append("if(!(\$0 instanceof ${Constants.INJECTABLE_CLASSNAME})){return;}")
            stringBuilder.append(
                    if (!isFragment) "android.content.Intent var = getIntent()"
                    else "android.os.Bundle var = getArguments();")
                    .append("if(var == null){return;}")
            infoList.forEach {
                stringBuilder.append("${it.fieldName} = ")
                        .append(getPrefixStatement(it))
                        .append(getExtraStatement(isFragment, it))
            }
        }
        stringBuilder.append("}")
        return stringBuilder.toString()
    }

    private fun getPrefixStatement(injectInfo: InjectInfo): String {
        val type = injectInfo.type
        val classType = injectInfo.classType
        return when (values()[type]) {
            SERIALIZABLE, PARCELABLE_ARRAY, PARCELABLE -> "(${classType.name})var."
            else -> "var."
        }
    }

    private fun getExtraStatement(isFragment: Boolean, injectInfo: InjectInfo): String {
        val type = injectInfo.type
        val annotationName = injectInfo.annotationName
        val fieldName = injectInfo.fieldName
        return when (values()[type]) {
            INTEGER -> {
                if (isFragment) "getInt(\"$annotationName\", $fieldName);"
                else "getIntExtra(\"$annotationName\", $fieldName);"
            }
            INT_ARRAY -> {
                if (isFragment) "getIntArray(\"$annotationName\");"
                else "getIntArrayExtra(\"$annotationName\");"
            }
            INT_LIST -> {
                if (isFragment) "getIntegerArrayList(\"$annotationName\");"
                else "getIntegerArrayListExtra(\"$annotationName\");"
            }
            BOOLEAN -> {
                if (isFragment) "getBoolean(\"$annotationName\", $fieldName);"
                else "getBooleanExtra(\"$annotationName\", $fieldName);"
            }
            BOOLEAN_ARRAY -> {
                if (isFragment) "getBooleanArray(\"$annotationName\");"
                else "getBooleanArrayExtra(\"$annotationName\");"
            }
            STRING -> {
                if (isFragment) "getString(\"$annotationName\");"
                else "getStringExtra(\"$annotationName\");"
            }
            STRING_ARRAY -> {
                if (isFragment) "getStringArray(\"$annotationName\");"
                else "getStringArrayExtra(\"$annotationName\");"
            }
            STRING_LIST -> {
                if (isFragment) "getStringArrayList(\"$annotationName\");"
                else "getStringArrayListExtra(\"$annotationName\");"
            }
            CHARACTER -> {
                if (isFragment) "getChar(\"$annotationName\", $fieldName);"
                else "getCharExtra(\"$annotationName\", $fieldName);"
            }
            CHAR_ARRAY -> {
                if (isFragment) "getCharArray(\"$annotationName\");"
                else "getCharArrayExtra(\"$annotationName\");"
            }
            SERIALIZABLE -> {
                if (isFragment) "getSerializable(\"$annotationName\");"
                else "getSerializableExtra(\"$annotationName\");"
            }
            PARCELABLE -> {
                if (isFragment) "getParcelable(\"$annotationName\");"
                else "getParcelableExtra(\"$annotationName\");"
            }
            PARCELABLE_ARRAY -> {
                if (isFragment) "getParcelableArray(\"$annotationName\");"
                else "getParcelableArrayExtra(\"$annotationName\");"
            }
            PARCELABLE_LIST -> {
                if (isFragment) "getParcelableArrayList(\"$annotationName\");"
                else "getParcelableArrayListExtra(\"$annotationName\");"
            }
            BYTE -> {
                if (isFragment) "getByte(\"$annotationName\", $fieldName);"
                else "getByteExtra(\"$annotationName\", $fieldName);"
            }
            BYTE_ARRAY -> {
                if (isFragment) "getByteArray(\"$annotationName\");"
                else "getByteArrayExtra(\"$annotationName\");"
            }
            DOUBLE -> {
                if (isFragment) "getDouble(\"$annotationName\", $fieldName);"
                else "getDoubleExtra(\"$annotationName\", $fieldName);"
            }
            DOUBLE_ARRAY -> {
                if (isFragment) "getDoubleArray(\"$annotationName\");"
                else "getDoubleArrayExtra(\"$annotationName\");"
            }
            FLOAT -> {
                if (isFragment) "getFloat(\"$annotationName\", $fieldName);"
                else "getFloatExtra(\"$annotationName\", $fieldName);"
            }
            FLOAT_ARRAY -> {
                if (isFragment) "getFloatArray(\"$annotationName\");"
                else "getFloatArrayExtra(\"$annotationName\");"
            }
            LONG -> {
                if (isFragment) "getLong(\"$annotationName\", $fieldName);"
                else "getLongExtra(\"$annotationName\", $fieldName);"
            }
            LONG_ARRAY -> {
                if (isFragment) "getLongArray(\"$annotationName\");"
                else "getLongArrayExtra(\"$annotationName\");"
            }
            SHORT -> {
                if (isFragment) "getShort(\"$annotationName\", $fieldName);"
                else "getShortExtra(\"$annotationName\", $fieldName);"
            }
            SHORT_ARRAY -> {
                if (isFragment) "getShortArray(\"$annotationName\");"
                else "getShortArrayExtra(\"$annotationName\");"
            }
            CHAR_SEQUENCE -> {
                if (isFragment) "getCharSequence(\"$annotationName\");"
                else "getCharSequenceExtra(\"$annotationName\");"
            }
            CHAR_SEQUENCE_ARRAY -> {
                if (isFragment) "getCharSequenceArray(\"$annotationName\");"
                else "getCharSequenceArrayExtra(\"$annotationName\");"
            }
            CHAR_SEQUENCE_LIST -> {
                if (isFragment) "getCharSequenceArrayList(\"$annotationName\");"
                else "getCharSequenceArrayListExtra(\"$annotationName\");"
            }
        }
    }

    private fun genRouterProviderImpl(dir: File, group: String, nodeList: List<NodeInfo>) {
        MethodGen(Constants.ROUTER_GEN_FILE_PACKAGE + genRouterProviderClassName(group))
                .interfaces(IRouterNodeProvider::class.java)
                .signature(returnStatement = "public java.util.List",
                        name = "getRouterNodes")
                .body { produceNodesMethodBodySrc(nodeList) }
                .gen()?.writeFile(dir.absolutePath)
    }

    private fun produceNodesMethodBodySrc(nodeList: List<NodeInfo>): String {
        val builder = StringBuilder("{")
                .append("java.util.ArrayList list = new java.util.ArrayList();")
        nodeList.forEach {
            val typeStr = "${Constants.NODE_TYPE_CLASSNAME}.${it.type}"
            builder.append("list.add(${Constants.NODE_FACTORY_CLASSNAME}.produceRouterNode($typeStr, \"${it.path}\", ${it.ctClass.name}.class));")
        }
        builder.append("return list;")
                .append("}")
        return builder.toString()
    }

    private fun genRouterLazyLoaderImpl(dir: File, groupMap: MutableMap<String, ArrayList<String>>) {
        val name = Constants.ROUTER_GEN_FILE_PACKAGE + Constants.LAZY_LOADER_IMPL_NAME
        MethodGen(name)
                .interfaces(IRouterLazyLoader::class.java)
                .signature("public java.util.List",
                        "lazyLoadFactoryByGroup",
                        "(String group)")
                .body { produceLazyLoadFactoryBodySrc(groupMap) }
                .gen()?.writeFile(dir.absolutePath)
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
                    sb.append("result.add(new ${Constants.ROUTER_GEN_FILE_PACKAGE}$name());")
                }
                sb.append("return result;}")
            }
            sb.append("default:break;}")
        }
        sb.append("return result;}")
        return sb.toString()
    }

    private fun genRouterProviderClassName(group: String): String {
        val componentName = getComponentExtension().componentName
        return "$group\$$componentName\$NodeProvider"
    }

    private fun getComponentExtension(): ComponentExtension {
        return project.extensions.findByType(ComponentExtension::class.java)
                ?: throw GradleException("can not find ComponentExtension, please check your build.gradle file")
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


    data class NodeInfo(val path: String,
                        val ctClass: CtClass) {
        val type = getNodeType(ctClass)

        private fun getNodeType(ctClass: CtClass): NodeType {
            val classPool = InjectHelper.instance.getClassPool()
            NodeType.values().forEach { nodeType ->
                nodeType.supportClasses().forEach support@{
                    if (it == "") {
                        return@support
                    }
                    try {
                        val supportClass = classPool[it]
                        if (ctClass.subtypeOf(supportClass)) {
                            return nodeType
                        }
                    } catch (e: Exception) {
                        Log.e("cannot got $it in ctClass when check node type. " + e.message)
                    }
                }
            }
            return NodeType.UNSPECIFIED
        }
    }

    data class InjectInfo(val ctField: CtField,
                          val annotationName: String,
                          private val ctClass: CtClass) {
        companion object {
            private val classPool = InjectHelper.instance.getClassPool()
            private val serializableType = classPool["java.io.Serializable"]
            private val parcelableType = classPool["android.os.Parcelable"]
            private val charSequenceType = classPool["java.lang.CharSequence"]
        }

        var type: Int = 0
        val fieldName: String = ctField.name
        val classType: CtClass = ctField.type

        init {
            type = initType()
        }

        /**
         * initType() method will decide and check the type of the field which transport by bundle
         */
        private fun initType(): Int {
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

                //keep these for abstract type field
                "java.io.Serializable" -> SERIALIZABLE.ordinal
                "android.os.Parcelable" -> PARCELABLE.ordinal
                "java.lang.CharSequence" -> CHAR_SEQUENCE.ordinal

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
                "android.os.Parcelable[]" -> PARCELABLE_ARRAY.ordinal

                //process put[...]ArrayList type
                "java.util.ArrayList" -> {
                    val fieldInfo = ctClass.classFile.fields.find { it is FieldInfo && it.name == fieldName } as FieldInfo
                    val sa = fieldInfo.getAttribute(SignatureAttribute.tag) as SignatureAttribute
                    //do this for get generic type int ArrayList,
                    //check this type whether fit for bundle support type
                    val type = SignatureAttribute.toFieldSignature(sa.signature).jvmTypeName()
                    return when (val genericType = extractGeneric(type)) {
                        "java.lang.String" -> STRING_LIST.ordinal
                        "java.lang.Integer" -> INT_LIST.ordinal
                        //keep this for abstract
                        "java.lang.CharSequence" -> CHAR_SEQUENCE_LIST.ordinal
                        else -> {
                            val ctClass = classPool[genericType]
                            when {
                                ctClass.subtypeOf(parcelableType) -> PARCELABLE_LIST.ordinal
                                ctClass.subtypeOf(charSequenceType) -> CHAR_SEQUENCE_LIST.ordinal
                                else -> throw InjectTypeException(ctClass.name, fieldName, type)
                            }
                        }
                    }
                }
                else -> {
                    when {
                        //process put[...]Array type
                        classType.isArray && classType.componentType.subtypeOf(parcelableType) -> PARCELABLE_ARRAY.ordinal
                        classType.isArray && classType.componentType.subtypeOf(charSequenceType) -> CHAR_SEQUENCE_ARRAY.ordinal
                        //process putParcelable/putSerializable type
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