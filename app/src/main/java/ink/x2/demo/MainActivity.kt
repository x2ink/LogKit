package ink.x2.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ink.x2.logkit.LogKit

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        LogKit.config=LogKit.Config(logType = LogKit.LogType.LINE)
        testLogKit()
    }

    private fun testLogKit() {
        val text = "你好啊\n我是小王"
        val number = 123
        val user = User(name = "小王", age = 18)
        val json = """{"name":"小王","age":18,"tags":["a","b"]}"""

        // VERBOSE
        LogKit.v("V-String") { text }
        LogKit.v("V-Number") { number }
        LogKit.v("V-Object") { user }
        LogKit.v("V-JSON") { json }

        // DEBUG
        LogKit.d("D-String") { text }
        LogKit.d("D-Number") { number }
        LogKit.d("D-Object") { user }
        LogKit.d("D-JSON") { json }

        // INFO
        LogKit.i("I-String") { text }
        LogKit.i("I-Number") { number }
        LogKit.i("I-Object") { user }
        LogKit.i("I-JSON") { json }

        // WARN
        LogKit.w("W-String") { text }
        LogKit.w("W-Number") { number }
        LogKit.w("W-Object") { user }
        LogKit.w("W-JSON") { json }

        // ERROR
        LogKit.e("E-String") { text }
        LogKit.e("E-Number") { number }
        LogKit.e("E-Object") { user }
        LogKit.e("E-JSON") { json }
    }

    private fun simulatePlayerFailure() {
        throw IllegalStateException(
            "模拟播放失败",
            IllegalArgumentException("mediaId 不能为空")
        )
    }

    private data class User(
        val name: String,
        val age: Int
    )

    private class PlainUser(
        val name: String,
        val age: Int
    )
}