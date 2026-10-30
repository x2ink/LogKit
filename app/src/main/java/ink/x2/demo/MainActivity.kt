package ink.x2.demo

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ink.x2.logkit.LogKit
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val logDirectory = File(requireNotNull(getExternalFilesDir(null)), "logs")
        check(logDirectory.isDirectory || logDirectory.mkdirs()) {
            "无法创建日志目录：${logDirectory.absolutePath}"
        }
        if (LogKit.config.logFilePath != logDirectory) {
            LogKit.init(
                LogKit.Config(
                    logFilePath = logDirectory,
                    logMaxBuffer = LogcatTestCases.LOG_FILE_BYTES,
                    minLevel = Log.VERBOSE,
                    logType = LogKit.LogType.LINE,
                ),
            )
        }
        val resultView = findViewById<TextView>(R.id.test_result)
        val header = "日志目录：${logDirectory.absolutePath}\n文件轮转阈值：5MB\n\n"
        resultView.text = header + "正在执行 VERBOSE / Logcat 落盘测试…"
        Thread(
            {
                runCatching {
                    LogcatTestCases(logDirectory).run { progress ->
                        showResult(resultView, header + progress)
                    }
                }.onFailure { failure ->
                    showResult(resultView, header + "测试失败：\n${failure.stackTraceToString()}")
                    Log.v("LogcatTestResult", "测试失败", failure)
                }
            },
            "logcat-test",
        ).start()
    }

    private fun showResult(view: TextView, text: String) {
        runOnUiThread {
            if (!isDestroyed && !isFinishing) {
                view.text = text
            }
        }
    }
}
