package ink.x2.record

import android.Manifest
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ink.x2.record.databinding.ActivityMainBinding
import ink.x2.record.repository.RecordRepository
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var recordRepository: RecordRepository

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        recordRepository.initAduioRecord()
        binding.btStartRecord.setOnClickListener {
            val file = File(
                getExternalFilesDir(null),
                "record_${System.currentTimeMillis()}.wav"
            )
            recordRepository.startRecording(file)
        }
        binding.btStopRecord.setOnClickListener {
            recordRepository.stopRecording()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED){
               launch {
                   recordRepository.amplitude.collect{value->
                       binding.wvWave.addAmplitude(value)
                   }
               }
                launch {
                    recordRepository.recording.collect { value ->
                        binding.btStartRecord.isVisible = !value
                        binding.btStopRecord.isVisible = value
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recordRepository.releaseRecorder()
    }
}