package cn.soul.android.plugin.component.utils

import com.google.common.collect.ImmutableSet

/**
 * @author panxinghai
 *
 * date : 2019-07-18 10:34
 */
object Log {
    enum class Level {
        ERROR,
        WARNING,
        PROCESS,
        DETAIL,
        INFO
    }

    val entireLevel: ImmutableSet<Level> = ImmutableSet.of(Log.Level.ERROR, Log.Level.WARNING, Log.Level.PROCESS, Log.Level.DETAIL, Log.Level.INFO)

    var logLevel: ImmutableSet<Level> = entireLevel

    private const val defaultTag = "[component plugin]-"

    fun e(tag: String, msg: String) {
        doErrorLog(Log.Level.ERROR, tag, msg)
    }

    fun w(tag: String, msg: String) {
        doNormalLog(Log.Level.WARNING, tag, msg)
    }

    fun p(tag: String, msg: String) {
        doNormalLog(Log.Level.PROCESS, tag, msg)
    }

    fun d(tag: String, msg: String) {
        doNormalLog(Log.Level.DETAIL, tag, msg)
    }

    fun i(msg: String) {
        doNormalLog(Log.Level.INFO, msg = msg)
    }

    fun e(msg: String) {
        doErrorLog(Log.Level.ERROR, msg = "error: $msg")
    }

    fun w(msg: String) {
        doNormalLog(Log.Level.WARNING, msg = msg)
    }

    fun p(msg: String) {
        doNormalLog(Log.Level.PROCESS, msg = msg)
    }

    fun d(msg: String) {
        doNormalLog(Log.Level.DETAIL, msg = msg)
    }

    fun i(tag: String = defaultTag, msg: String) {
        doNormalLog(Log.Level.INFO, tag, msg)
    }

    private fun doNormalLog(level: Level, tag: String = defaultTag, msg: String) {
        if (inScope(level)) {
            println("$tag $msg")
        }
    }

    private fun doErrorLog(level: Level, tag: String = defaultTag, msg: String) {
        if (inScope(level)) {
            System.err.println("$tag $msg")
        }
    }

    fun inScope(level: Level) = logLevel.contains(level)
}