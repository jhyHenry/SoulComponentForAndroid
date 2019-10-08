package cn.soul.android.plugin.component.utils

import javassist.ClassClassPath
import javassist.ClassPool
import javassist.CtClass
import java.io.File

/**
 * Created by nebula on 2019-07-20
 */
class InjectHelper private constructor() {
    private var mClassPool: ClassPool? = null

    companion object {
        val instance: InjectHelper by lazy {
            InjectHelper()
        }
    }

    fun refresh() {
        mClassPool = ClassPool(null)
        mClassPool?.appendSystemPath()
    }

    fun appendClassPath(path: String) {
        mClassPool?.insertClassPath(path)
    }

    fun appendClassPath(path: ClassClassPath) {
        mClassPool?.appendClassPath(path)
    }

    fun getClassPool(): ClassPool {
        if (mClassPool == null) {
            refresh()
        }
        return mClassPool!!
    }

    fun processFiles(file: File): ActionContainer {
        val bufferList = mutableListOf<File>()
        val packageIndex = file.absolutePath.length + 1
        traversalFiles(file, bufferList)
        return ActionContainer(bufferList, packageIndex)
    }

    private fun traversalFiles(file: File, bufferList: MutableList<File>) {
        if (file.isDirectory) {
            file.listFiles()?.forEach {
                traversalFiles(it, bufferList)
            }
        }
        bufferList.add(file)
    }

    inner class ActionContainer(
            private val list: MutableList<File>,
            private val packageIndex: Int
    ) {
        private var mNameFilterAction: ((File) -> Boolean)? = null
        private var mClassFilterAction: ((CtClass) -> Boolean)? = null

        fun nameFilter(action: (file: File) -> Boolean): ActionContainer {
            mNameFilterAction = action
            return this
        }

        fun classFilter(action: (ctClass: CtClass) -> Boolean): ActionContainer {
            mClassFilterAction = action
            return this
        }

        fun files(): MutableList<File> = list

        fun forEach(action: (ctClass: CtClass) -> Unit) {
            list.forEach inner@{
                if (mNameFilterAction == null
                        || (mNameFilterAction != null && mNameFilterAction?.invoke(it)!!)) {
                    val classPool = this@InjectHelper.getClassPool()
                    val pathLength = it.absolutePath.length
                    val className = it.absolutePath.subSequence(packageIndex, pathLength - 6)
                            .toString().replace('/', '.')
                    val ctClass = classPool[className]
                    if (mClassFilterAction == null ||
                            (mClassFilterAction != null && mClassFilterAction?.invoke(ctClass)!!)) {
                        action.invoke(ctClass)
                    }
                }
            }
        }
    }
}