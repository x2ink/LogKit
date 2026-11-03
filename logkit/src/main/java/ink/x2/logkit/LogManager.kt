package ink.x2.logkit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.os.Process
import android.util.Log
import java.nio.file.Files
import kotlin.io.path.moveTo

class LogManager(private val maxBuffer: Long,private val pendingDir: File,private val uploadedDir: File) {

    suspend fun writeLogToFile(content: String)=withContext(Dispatchers.IO){
        val files = pendingDir.listFiles()?: throw IOException("无法读取目录: ${pendingDir.absolutePath}")
        val latestFile=files.filter { it.isFile }.maxByOrNull {
            it.name.removePrefix("LogKit_").removeSuffix(".log").toIntOrNull() ?: 0
        }
        var targetFile: File
        if (latestFile==null){
            targetFile=File(pendingDir,"LogKit_1.log")
        }else{
            if(latestFile.length()>maxBuffer){
                val index=latestFile.name.removePrefix("LogKit_").removeSuffix(".log").toInt()
                targetFile=File(pendingDir,"LogKit_${index+1}.log")
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

    suspend fun loadPendingLogs(startTime:Long,endTime:Long): List<File> = withContext(Dispatchers.IO){
        (pendingDir.listFiles()?.filter {
            it.lastModified() in (startTime + 1)..<endTime
        }) ?: throw IOException("无法读取目录: ${pendingDir.absolutePath}")
    }
    suspend fun archiveLogs(logPath: File): File = withContext(Dispatchers.IO) {
        if (!logPath.isFile) {
            throw IOException("源文件不存在或不是文件: ${logPath.absolutePath}")
        }
        if (!uploadedDir.exists() && !uploadedDir.mkdirs()) {
            throw IOException("无法创建目标目录: ${uploadedDir.absolutePath}")
        }
        val baseName = logPath.nameWithoutExtension
        val ext = logPath.extension.ifEmpty { "log" }
        val newFileName = "${baseName}_${System.currentTimeMillis()}.$ext"
        val newFile = uploadedDir.resolve(newFileName)
        try {
            logPath.toPath().moveTo(newFile.toPath(), overwrite = false)
        }catch (e: IOException) {
            throw IOException("移动日志文件失败: ${logPath.absolutePath} -> ${newFile.absolutePath}", e)
        }
        newFile
    }
    suspend fun deleteLogs(logPath: File): Boolean=withContext(Dispatchers.IO){
        Files.deleteIfExists(logPath.toPath())
    }
}