package ink.x2.record.repository
import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import ink.x2.logkit.LogKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
class RecordRepository @Inject constructor(){

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _amplitude = MutableStateFlow(0.0f)
    val amplitude = _amplitude.asStateFlow()
    val recordScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var recorder: AudioRecord
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun initAduioRecord(){
        recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(32000)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            )
            .setBufferSizeInBytes(64000).build()
    }
    fun RandomAccessFile.writeWavHeader() {
        writeBytes("RIFF")
        writeIntLE(36)
        writeBytes("WAVE")
        writeBytes("fmt ")
        writeIntLE(16)
        writeShortLE(1)
        writeShortLE(1)
        writeIntLE(32000)
        writeIntLE(64000)
        writeShortLE(2)
        writeShortLE(16)
        writeBytes("data")
        writeIntLE(0)
    }
    private fun RandomAccessFile.writeIntLE(value: Long) {
        for (i in 0..3) {
            write(((value ushr (i * 8)) and 0xFF).toInt())
        }
    }

    private fun RandomAccessFile.writeShortLE(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }
    fun startRecording(outputFile: File){
        if (recording.value) return
        recordScope.launch(Dispatchers.IO) {
            val buffer = ShortArray(1024)
            val pcmBytes = ByteArray(buffer.size * 2)
            try {
                RandomAccessFile(outputFile,"rw").use { wav->
                    wav.setLength(0)
                    wav.writeWavHeader()
                    try {
                        recorder.startRecording()
                        _recording.update { !it }
                        while (recording.value&&isActive){
                            val count=recorder.read(buffer,0,buffer.size)
                            if (count > 0) {
                                var peak = 0
                                for (index in 0 until count) {
                                    val sample=buffer[index].toInt()
                                    val value = kotlin.math.abs(sample)
                                    peak = maxOf(peak, value)
                                    pcmBytes[index * 2] = sample.toByte()
                                    pcmBytes[index * 2 + 1] =
                                        (sample shr 8).toByte()
                                }
                                _amplitude.update {
                                    peak / 32768f
                                }
                                wav.write(pcmBytes, 0, count * 2)
                            }
                        }
                    }catch (re: Exception){
                        LogKit.d("error"){re}
                    }
                    val fileLength = wav.length()
                    wav.seek(4L)
                    wav.writeIntLE(fileLength - 8L)
                    wav.seek(40L)
                    wav.writeIntLE(fileLength - 44L)
                }
            }catch (e: Exception){
                LogKit.d("error"){e}
            }finally {
                LogKit.v("msg"){
                    "写入成功"
                }
            }
        }
    }
    fun stopRecording(){
        recordScope.launch(Dispatchers.IO) {
            _recording.update { !it }
            recorder.stop()
        }
    }
    fun releaseRecorder(){
        recordScope.launch (Dispatchers.IO){
            recorder.release()
        }
    }

}