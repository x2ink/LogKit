package ink.x2.logkit

import java.io.File

abstract class LogUploadAdapter(
    val intervalMillis: Long,
){
    abstract fun upload(files: List<File>)

    fun start(){

    }
    fun stop(){

    }

}