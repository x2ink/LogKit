package ink.x2.demo

import ink.x2.logkit.LogKit
import org.json.JSONArray
import org.json.JSONObject

class LongLogTestCases(
    private val checkpoint: (String) -> Unit,
    private val readLogs: () -> String,
) {

    fun testSplitBoundaries() {
        val originalConfig = LogKit.config
        try {
            val limits = listOf(Int.MIN_VALUE, -1, 0, 1, 2, 3, 4, 16, 1000, 4000, 5000, Int.MAX_VALUE)
            for (configuredSize in limits) {
                LogKit.config = originalConfig.copy(lineSize = configuredSize)
                val limit = configuredSize.coerceIn(4, 4000)
                val samples = listOf(
                    "", "a".repeat(limit - 1), "a".repeat(limit), "a".repeat(limit + 1),
                    "a".repeat(limit * 2), "a".repeat(limit - 1) + "中",
                    "a".repeat(limit - 1) + "😀", "中".repeat(3000),
                    "😀".repeat(2001), "中文😀abc\n".repeat(1000),
                )
                samples.forEach { message ->
                    val chunks = LogKit.splitLog(message)
                    check(chunks.isNotEmpty() && chunks.joinToString("") == message) {
                        "lineSize=$configuredSize 分段后原文无法还原"
                    }
                    check(chunks.all { it.toByteArray(Charsets.UTF_8).size <= limit }) {
                        "lineSize=$configuredSize 存在超过 $limit 字节的分段"
                    }
                    check(message.isEmpty() || chunks.none(String::isEmpty)) {
                        "lineSize=$configuredSize 产生无效的空分段"
                    }
                }
                val asciiChunks = LogKit.splitLog("a".repeat(limit * 2 + 1))
                check(asciiChunks.map(String::length) == listOf(limit, limit, 1)) {
                    "lineSize=$configuredSize 未按生效值 $limit 分段"
                }
            }
        } finally {
            LogKit.config = originalConfig
        }
    }

    fun testLongLogPersistence() {
        val originalConfig = LogKit.config
        var caseIndex = 0
        try {
            for (format in LogKit.LogType.entries) {
                for (lineSize in listOf(1000, 4000)) {
                    LogKit.config = originalConfig.copy(logType = format, lineSize = lineSize)
                    for (case in outputCases()) {
                        val tag = "Long${caseIndex++}"
                        LogKit.v(tag) { case.value }
                        checkpoint("${tag}_END")
                        verifyPersisted(tag, format, lineSize, case)
                    }
                }
            }
        } finally {
            LogKit.config = originalConfig
        }
    }

    private fun verifyPersisted(tag: String, format: LogKit.LogType, limit: Int, case: OutputCase) {
        val outputTag = when (format) {
            LogKit.LogType.LINE -> "LogKit-$tag-${Thread.currentThread().name}"
            LogKit.LogType.BORDER -> "LogKit-$tag"
        }
        val messages = readLogs().lineSequence()
            .filter { "$outputTag: " in it }
            .map { it.substringAfter("$outputTag: ") }
            .toList()
        val bodies = when (format) {
            LogKit.LogType.LINE -> messages
            LogKit.LogType.BORDER -> messages
                .filter { it.startsWith("| ") && it != "| Thread：${Thread.currentThread().name}" }
                .map { it.removePrefix("| ") }
        }
        val expected = when (format) {
            LogKit.LogType.LINE -> case.lineText
            LogKit.LogType.BORDER -> case.borderText
        }
        check(bodies.size > 1) { "${case.name}/$format/$limit 未收集到长日志分段" }
        check(bodies.all { it.toByteArray(Charsets.UTF_8).size <= limit }) {
            "${case.name}/$format/$limit 有分段超过字节上限"
        }
        check(bodies.joinToString("") == expected.replace("\n", "")) {
            "${case.name}/$format/$limit 落盘内容缺失、重复、乱序或字符损坏"
        }
        if (format == LogKit.LogType.BORDER) {
            check(messages.first().startsWith("┌") && messages.last().startsWith("└")) {
                "${case.name} 的边框不完整"
            }
        }
    }

    private fun outputCases(): List<OutputCase> {
        val ascii = "a".repeat(8001)
        val chinese = "中".repeat(4001)
        val emoji = "a".repeat(3999) + "😀".repeat(2001)
        val mixed = "中文😀abc".repeat(1000)
        val multiline = "$ascii\n$chinese\n$emoji"
        val jsonObject = JSONObject().put("message", mixed)
        val jsonArray = JSONArray().put(mixed).put(ascii)
        val failure = IllegalStateException(mixed, IllegalArgumentException("长异常 cause"))
        val plainObject = object {
            override fun toString(): String = mixed
        }
        return listOf(
            OutputCase("ASCII", ascii, ascii),
            OutputCase("中文", chinese, chinese),
            OutputCase("emoji", emoji, emoji),
            OutputCase("中英混合", mixed, mixed),
            OutputCase("多行", multiline, multiline),
            OutputCase("JSON 对象", jsonObject.toString(), jsonObject.toString(), jsonObject.toString(2)),
            OutputCase("JSON 数组", jsonArray.toString(), jsonArray.toString(), jsonArray.toString(2)),
            OutputCase("普通对象", plainObject, mixed),
            OutputCase("异常堆栈", failure, failure.stackTraceToString()),
        )
    }

    private data class OutputCase(
        val name: String,
        val value: Any,
        val lineText: String,
        val borderText: String = lineText,
    )
}
