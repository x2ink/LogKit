package ink.x2.logkit

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object LogKit {
    const val TAG = "LogKit"
    private val TOP_BORDER = "┌" + "─".repeat(50)
    private val MIDDLE_BORDER = "├" + "┄".repeat(50)
    private val BOTTOM_BORDER = "└" + "─".repeat(50)

    enum class LogType {
        BORDER,
        LINE
    }

    data class Config(
        val enable: Boolean = true,
        val minLevel: Int = Log.VERBOSE,
        val logFilePath: File? = null,
        val logMaxBuffer: Long = 5L * 1024 * 1024,
        val logType: LogType = LogType.BORDER
    )

    var config: Config = Config()
    val printLock = Any()
    private val logScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    lateinit var logManager: LogManager
    private var collectJob: Job? = null

    @Synchronized
    fun init(initConfig: Config) {
        if (collectJob?.isActive == true) return
        config = initConfig
        config.logFilePath?.let {
            logManager = LogManager(it, config.logMaxBuffer)
            collectJob = logScope.launch {
                logManager.collectLogcat(config.minLevel)
            }
        }
    }

    fun v(tag: String, msg: () -> Any) {
        if (!config.enable) return
        if (config.minLevel > Log.VERBOSE) return
        synchronized(printLock) {
            val threadName = Thread.currentThread().name
            when (config.logType) {
                LogType.LINE -> {
                    printLineLog(tag, threadName, msg) { key, value ->
                        Log.v(key, value)
                    }
                }

                LogType.BORDER -> {
                    printBorderLog(tag, threadName, msg) { key, value ->
                        Log.v(key, value)
                    }
                }
            }
        }

    }

    fun d(tag: String, msg: () -> Any) {
        if (!config.enable) return
        if (config.minLevel > Log.DEBUG) return
        synchronized(printLock) {
            val threadName = Thread.currentThread().name
            when (config.logType) {
                LogType.LINE -> {
                    printLineLog(tag, threadName, msg) { key, value ->
                        Log.d(key, value)
                    }
                }

                LogType.BORDER -> {
                    printBorderLog(tag, threadName, msg) { key, value ->
                        Log.d(key, value)
                    }
                }
            }
        }
    }

    fun i(tag: String, msg: () -> Any) {
        if (!config.enable) return
        if (config.minLevel > Log.INFO) return
        synchronized(printLock) {
            val threadName = Thread.currentThread().name
            when (config.logType) {
                LogType.LINE -> {
                    printLineLog(tag, threadName, msg) { key, value ->
                        Log.i(key, value)
                    }
                }

                LogType.BORDER -> {
                    printBorderLog(tag, threadName, msg) { key, value ->
                        Log.i(key, value)
                    }
                }
            }
        }
    }

    fun w(tag: String, msg: () -> Any) {
        if (!config.enable) return
        if (config.minLevel > Log.WARN) return
        synchronized(printLock) {
            val threadName = Thread.currentThread().name
            when (config.logType) {
                LogType.LINE -> {
                    printLineLog(tag, threadName, msg) { key, value ->
                        Log.w(key, value)
                    }
                }

                LogType.BORDER -> {
                    printBorderLog(tag, threadName, msg) { key, value ->
                        Log.w(key, value)
                    }
                }
            }
        }
    }

    fun e(tag: String, msg: () -> Any) {
        if (!config.enable) return
        if (config.minLevel > Log.ERROR) return
        synchronized(printLock) {
            val threadName = Thread.currentThread().name
            when (config.logType) {
                LogType.LINE -> {
                    printLineLog(tag, threadName, msg) { key, value ->
                        Log.e(key, value)
                    }
                }

                LogType.BORDER -> {
                    printBorderLog(tag, threadName, msg) { key, value ->
                        Log.e(key, value)
                    }
                }
            }
        }
    }

    fun printLineLog(
        tag: String,
        threadName: String,
        msg: () -> Any,
        print: (tag: String, value: String) -> Unit
    ) {
        when (val text = msg()) {
            is String -> {
                val sType = isJson(text)
                if (sType.first) {
                    var printText = text;
                    when (sType.second) {
                        is JSONObject -> {
                            printText = JSONObject(text).toString()
                        }

                        is JSONArray -> {
                            printText = JSONArray(text).toString()
                        }
                    }
                    print("$TAG-$tag-$threadName", printText)
                } else {
                    val lines = text.split("\n")
                    lines.forEach {
                        print("$TAG-$tag-$threadName", it)
                    }
                }
            }

            is Number -> {
                print("$TAG-$tag-$threadName", text.toString())
            }

            is Exception -> {
                print("$TAG-$tag-$threadName", text.stackTraceToString())
            }

            else -> {
                print("$TAG-$tag-$threadName", text.toString())
            }
        }
    }

    fun printBorderLog(
        tag: String,
        threadName: String,
        msg: () -> Any,
        print: (tag: String, value: String) -> Unit
    ) {
        print("$TAG-$tag", TOP_BORDER)
        print("$TAG-$tag", "| Thread：$threadName")
        print("$TAG-$tag", MIDDLE_BORDER)
        when (val text = msg()) {
            is String -> {
                val sType = isJson(text)
                if (sType.first) {
                    var printText = text;
                    when (sType.second) {
                        is JSONObject -> {
                            printText = JSONObject(text).toString(2)
                        }

                        is JSONArray -> {
                            printText = JSONArray(text).toString(2)
                        }
                    }
                    printText.lineSequence().forEach {
                        print("$TAG-$tag", "| $it")
                    }
                } else {
                    val lines = text.split("\n")
                    lines.forEach {
                        print("$TAG-$tag", "| $it")
                    }
                }
            }

            is Number -> {
                print("$TAG-$tag", "| $text")
            }

            is Exception -> {
                text.stackTraceToString().lineSequence().forEach {
                    print("$TAG-$tag", "| $it")
                }
            }

            else -> {
                print("$TAG-$tag", "| $text")
            }
        }
        print("$TAG-$tag", BOTTOM_BORDER)
    }

    fun isJson(s: String): Pair<Boolean, Any?> {
        val t = s.trim()
        return try {
            when {
                t.startsWith("{") -> Pair(true, JSONObject(t))
                t.startsWith("[") -> Pair(true, JSONArray(t))
                else -> Pair(false, null)
            }
        } catch (e: Exception) {
            Pair(false, null)
        }
    }
}