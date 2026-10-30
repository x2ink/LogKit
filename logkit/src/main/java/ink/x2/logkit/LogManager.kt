package ink.x2.logkit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.os.Process
import android.util.Log

class LogManager(private val file: File,private val maxBuffer: Long) {

    suspend fun writeLogToFile(content: String)=withContext(Dispatchers.IO){
        val files = file.listFiles()?: throw IOException("无法读取目录: ${file.absolutePath}")
        val latestFile=files.filter { it.isFile }.maxByOrNull {
            it.name.removePrefix("LogKit_").removeSuffix(".log").toIntOrNull() ?: 0
        }
        var targetFile: File
        if (latestFile==null){
            targetFile=File(file,"LogKit_1.log")
        }else{
            if(latestFile.length()>maxBuffer){
                val index=latestFile.name.removePrefix("LogKit_").removeSuffix(".log").toInt()
                targetFile=File(file,"LogKit_${index+1}.log")
            }else{
                targetFile=latestFile
            }
        }
        FileOutputStream(targetFile, true).bufferedWriter().use { out ->
            out.write(content)
            out.newLine()
        }
    }
    private fun levelToLogcatPriority(level: Int): String {
        return when (level) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "F"
            else -> "V"
        }
    }
    suspend fun collectLogcat(minLevel: Int)=withContext(Dispatchers.IO){
        val pid = Process.myPid()
        val priority = levelToLogcatPriority(minLevel)
        val process = ProcessBuilder(
            "logcat",
            "--pid=$pid",
            "-v",
            "threadtime",
            "*:$priority"
        ).start()

        process.inputStream
            .bufferedReader()
            .useLines { lines ->
                lines.forEach { line ->
                   writeLogToFile(line)
                }
            }
    }
}