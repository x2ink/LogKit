package ink.x2.demo

import android.os.Process
import android.os.SystemClock
import android.util.Log
import ink.x2.logkit.LogKit
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

class LogcatTestCases(private val logDirectory: File) {

    private val runId = "test-${System.nanoTime()}"
    private val firstFileIndex = logFiles().lastOrNull()?.let(::fileIndex) ?: 1
    private val passed = mutableListOf<String>()

    fun run(onProgress: (String) -> Unit) {
        check(running.compareAndSet(false, true)) { "已有日志测试正在执行" }
        val originalConfig = LogKit.config
        try {
            check(originalConfig.logMaxBuffer == LOG_FILE_BYTES) { "轮转阈值必须为 5MB" }
            check(originalConfig.minLevel == Log.VERBOSE) { "收集器必须以 VERBOSE 等级初始化" }
            runCase("原生 Log.v 收集及 threadtime 格式", onProgress, ::testNativeLogcat)
            runCase("LogKit.v 数据类型及异常", onProgress, ::testVerboseValues)
            runCase("LINE / BORDER 输出格式", onProgress, ::testFormats)
            runCase("包装器过滤与原生 Logcat 收集", onProgress, ::testFiltering)
            runCase("3 个线程并发写入", onProgress, ::testConcurrentWrites)
            runCase("超过 5MB 后轮转并保留完整日志", onProgress, ::testRotation)
            onProgress(passed.joinToString("\n") + "\n\n全部通过，日志保留在上述目录。")
            Log.v("LogcatTestResult", "$runId ALL_PASSED")
        } finally {
            LogKit.config = originalConfig
            running.set(false)
        }
    }

    private fun runCase(name: String, onProgress: (String) -> Unit, test: () -> Unit) {
        onProgress(passed.joinToString("\n") + "\n正在测试：$name")
        test()
        passed += "通过：$name"
        onProgress(passed.joinToString("\n"))
    }

    private fun testNativeLogcat() {
        val marker = "$runId NATIVE_CAPTURE"
        Log.v(NATIVE_TAG, marker)
        val file = awaitPersisted(marker)
        val line = tail(file).lineSequence().first { it.contains(marker) }
        val threadtime = Regex("""^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+\s+${Process.myPid()}\s+\d+\s+V\s+$NATIVE_TAG\s*:""")
        check(threadtime.containsMatchIn(line)) { "未保留 Logcat 时间、PID、TID、等级和标签：$line" }
    }

    private fun testVerboseValues() {
        LogKit.config = LogKit.config.copy(logType = LogKit.LogType.LINE)
        LogKit.v("String") { "$runId 中文日志 😀" }
        LogKit.v("Multiline") { "$runId 第一行\n$runId 第二行" }
        LogKit.v("Empty") { "" }
        LogKit.v("Int") { 123 }
        LogKit.v("Long") { Long.MAX_VALUE }
        LogKit.v("Double") { 3.1415926 }
        LogKit.v("Boolean") { true }
        LogKit.v("Object") { TestUser(runId, 18) }
        LogKit.v("JsonObject") { """{"run":"$runId","items":[1,2]}""" }
        LogKit.v("JsonArray") { """["$runId",1,true]""" }
        LogKit.v("InvalidJson") { "{$runId invalid json" }
        val failure = IllegalStateException("$runId failure", IllegalArgumentException("$runId cause"))
        LogKit.v("Exception") { failure }
        emitAndAwait("VALUES_END")

        val text = readTestLogs()
        listOf(
            "$runId 中文日志 😀", "$runId 第一行", "$runId 第二行",
            "TestUser(name=$runId, age=18)", """{"run":"$runId","items":[1,2]}""",
            """["$runId",1,true]""", "{$runId invalid json",
            "IllegalStateException: $runId failure", "Caused by: java.lang.IllegalArgumentException: $runId cause",
        ).forEach { expected -> check(expected in text) { "日志缺失：$expected" } }
        mapOf("Int" to "123", "Long" to Long.MAX_VALUE.toString(), "Double" to "3.1415926", "Boolean" to "true")
            .forEach { (tag, value) ->
                check(text.lineSequence().any { "LogKit-$tag-logcat-test" in it && it.endsWith(": $value") }) {
                    "$tag 日志缺失"
                }
            }
        check(text.lineSequence().any { "LogKit-Empty-logcat-test" in it }) { "空字符串日志缺失" }
    }

    private fun testFormats() {
        val originalConfig = LogKit.config
        try {
            LogKit.config = originalConfig.copy(logType = LogKit.LogType.LINE)
            LogKit.v("Line") { "$runId LINE_FORMAT" }
            awaitPersisted("$runId LINE_FORMAT")
            LogKit.config = originalConfig.copy(logType = LogKit.LogType.BORDER)
            LogKit.v("Border") { "$runId BORDER_FORMAT" }
            emitAndAwait("FORMATS_END")
        } finally {
            LogKit.config = originalConfig
        }
        val lines = readTestLogs().lineSequence().toList()
        check(lines.any { "LogKit-Line-logcat-test" in it && it.endsWith(": $runId LINE_FORMAT") })
        check(lines.any { "LogKit-Border" in it && it.endsWith(": | $runId BORDER_FORMAT") })
        check(lines.any { "LogKit-Border" in it && "┌" in it })
        check(lines.any { "LogKit-Border" in it && "└" in it })
    }

    private fun testFiltering() {
        val originalConfig = LogKit.config
        try {
            LogKit.config = originalConfig.copy(enable = false)
            LogKit.v("Disabled") { error("关闭日志后仍然执行了消息构造") }
            emitAndAwait("NATIVE_WHILE_DISABLED")

            LogKit.config = originalConfig.copy(minLevel = Log.DEBUG)
            LogKit.v("Filtered") { error("VERBOSE 被过滤后仍然执行了消息构造") }
            // 收集进程的等级取自 init；修改包装器配置不会重启收集进程。
            emitAndAwait("NATIVE_WHILE_FILTERED")
        } finally {
            LogKit.config = originalConfig
        }
        LogKit.v("Restored") { "$runId VERBOSE_RESTORED" }
        awaitPersisted("$runId VERBOSE_RESTORED")
    }

    private fun testConcurrentWrites() {
        val workers = List(3) { worker ->
            Thread(
                {
                    repeat(10) { index ->
                        LogKit.v("Concurrent") { "$runId CONCURRENT worker=$worker index=$index END" }
                    }
                },
                "logcat-worker-$worker",
            )
        }
        workers.forEach(Thread::start)
        workers.forEach { it.join() }
        emitAndAwait("CONCURRENT_END")
        val text = readTestLogs()
        repeat(3) { worker ->
            repeat(10) { index ->
                val marker = "$runId CONCURRENT worker=$worker index=$index END"
                check(text.lineSequence().count { marker in it } == 1) { "日志缺失或重复：$marker" }
            }
        }
    }

    private fun testRotation() {
        val startMarker = "$runId ROTATION_START"
        Log.v(NATIVE_TAG, startMarker)
        val initialFile = awaitPersisted(startMarker)
        val payload = "x".repeat(3072)
        val count = (LOG_FILE_BYTES / payload.length).toInt() + 32
        repeat(count) { index ->
            Log.v(NATIVE_TAG, "$runId ROTATION index=$index END $payload")
            // 每批等待落盘，避免突发日志挤满 Logcat 缓冲区造成误判。
            if ((index + 1) % 8 == 0 || index == count - 1) {
                emitAndAwait("BATCH_$index")
            }
        }
        emitAndAwait("ROTATION_END")
        val files = logFiles().filter { fileIndex(it) >= fileIndex(initialFile) }
        check(files.size >= 2) { "写入超过 5MB 后没有创建下一个日志文件" }
        files.zipWithNext().forEach { (previous, next) ->
            check(previous.length() > LOG_FILE_BYTES) { "文件不足 5MB 就发生轮转：${previous.name}" }
            check(fileIndex(next) == fileIndex(previous) + 1) { "日志文件序号不连续" }
        }
        val indices = Regex("${Regex.escape(runId)} ROTATION index=(\\d+) END")
            .findAll(readTestLogs()).map { it.groupValues[1].toInt() }.toList()
        check(indices == (0 until count).toList()) { "轮转前后有日志丢失、重复或乱序" }
    }

    private fun emitAndAwait(name: String) {
        val marker = "$runId $name"
        Log.v(NATIVE_TAG, marker)
        awaitPersisted(marker)
    }

    private fun awaitPersisted(marker: String): File {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            logFiles().takeLast(2).firstOrNull { marker in tail(it) }?.let { return it }
            Thread.sleep(20)
        }
        error("10 秒内未收集到日志：$marker")
    }

    private fun tail(file: File): String = RandomAccessFile(file, "r").use { input ->
        val length = input.length()
        val start = maxOf(0L, length - 16 * 1024)
        input.seek(start)
        val bytes = ByteArray((length - start).toInt())
        input.readFully(bytes)
        bytes.toString(Charsets.UTF_8)
    }

    private fun logFiles(): List<File> {
        val files = checkNotNull(logDirectory.listFiles()) { "无法读取日志目录：$logDirectory" }
        return files.filter { it.isFile && FILE_NAME.matches(it.name) }.sortedBy(::fileIndex)
    }

    private fun fileIndex(file: File): Int = file.name.removePrefix("LogKit_").removeSuffix(".log").toInt()

    private fun readTestLogs(): String = logFiles()
        .filter { fileIndex(it) >= firstFileIndex }
        .joinToString("\n") { it.readText() }
        .substringAfter("$runId NATIVE_CAPTURE", missingDelimiterValue = "")

    private data class TestUser(val name: String, val age: Int)

    companion object {
        const val LOG_FILE_BYTES = 5L * 1024 * 1024
        private const val NATIVE_TAG = "NativeVerboseTest"
        private val FILE_NAME = Regex("LogKit_\\d+\\.log")
        private val running = AtomicBoolean(false)
    }
}
