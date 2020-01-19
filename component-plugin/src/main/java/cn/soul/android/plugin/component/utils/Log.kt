package cn.soul.android.plugin.component.utils

import com.google.common.collect.ImmutableSet

/**
 * @author panxinghai
 *
 * date : 2019-07-18 10:34
 */
object Log {
    enum class Level {
        //error info
        ERROR,
        //warning info
        WARNING,
        //information of plugin process, you can see the task or action's execution progress
        PROCESS,
        //detail of all task, it maybe great amount
        DETAIL,
        //some unnecessary log
        INFO
    }

    val entireLevel: ImmutableSet<Level> = ImmutableSet.of(Level.ERROR, Level.WARNING, Level.PROCESS, Level.DETAIL, Level.INFO)

    var logLevel: ImmutableSet<Level> = entireLevel

    private const val defaultTag = "[component]"

    fun e(tag: String, msg: String) {
        doErrorLog(Level.ERROR, tag, msg)
    }

    fun w(tag: String, msg: String) {
        doNormalLog(Level.WARNING, tag, msg)
    }

    fun p(tag: String, msg: String) {
        doNormalLog(Level.PROCESS, tag, msg)
    }

    fun d(tag: String, msg: String) {
        doNormalLog(Level.DETAIL, tag, msg)
    }

    fun i(msg: String) {
        doNormalLog(Level.INFO, msg = msg)
    }

    fun e(msg: String) {
        doErrorLog(Level.ERROR, msg = "error: $msg")
    }

    fun w(msg: String) {
        doNormalLog(Level.WARNING, msg = msg)
    }

    fun p(msg: String) {
        doNormalLog(Level.PROCESS, msg = msg)
    }

    fun d(msg: String) {
        doNormalLog(Level.DETAIL, msg = msg)
    }

    fun i(tag: String = defaultTag, msg: String) {
        doNormalLog(Level.INFO, tag, msg)
    }

    fun test(msg: String) {
        doNormalLog(Level.ERROR, "test", msg)
    }

    private fun doNormalLog(level: Level, tag: String = defaultTag, msg: String) {
        if (inScope(level)) {
            println("$tag: $msg")
        }
    }

    private fun doErrorLog(level: Level, tag: String = defaultTag, msg: String) {
        if (inScope(level)) {
            System.err.println("$tag $msg")
        }
    }

    fun inScope(level: Level) = logLevel.contains(level)
}