package zied.harzallah.rtmp

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.decoder.AudioDecoderInterface
import com.pedro.encoder.input.decoder.VideoDecoderInterface
import com.pedro.library.generic.GenericFromFile
import com.pedro.library.view.OpenGlView
import kotlinx.coroutines.*
import java.io.IOException
import java.lang.StrictMath.max
import java.util.*

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class FromFileActivity : ComponentActivity(), ConnectChecker, VideoDecoderInterface,
    AudioDecoderInterface {

    private lateinit var genericFromFile: GenericFromFile
    private var filePath: Uri? = null
    private var recordPath = ""
    private var touching by mutableStateOf(false)


    private val activityResult =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            filePath = uri
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            FromFileScreen()
        }
    }

    @Composable
    fun FromFileScreen() {
        var urlText by remember { mutableStateOf(TextFieldValue("rtsp://192.168.1.19:8554/live")) }
        var fileName by remember { mutableStateOf("") }
        var isStreaming by remember { mutableStateOf(false) }
        var isRecording by remember { mutableStateOf(false) }
        var progress by remember { mutableStateOf(0f) }

        // UI Content
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceAround,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AndroidView(
                factory = { context ->
                    OpenGlView(context).apply {
                        genericFromFile = GenericFromFile(
                            this,
                            this@FromFileActivity,
                            this@FromFileActivity,
                            this@FromFileActivity
                        )
                        genericFromFile.setLoopMode(true)

                    }
                }, modifier = Modifier.fillMaxWidth().height(300.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // URL input field
            BasicTextField(value = urlText,
                onValueChange = { urlText = it },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.padding(16.dp).background(Color.LightGray)
                    ) {
                        if (urlText.text.isEmpty()) {
                            Text("Enter Stream URL")
                        }
                        innerTextField()
                    }
                })

            // Display the file name if selected
            Text(text = "Selected file: $fileName")

            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = {
                    startStopStream(urlText.text, isStreaming, isRecording) { it ->
                        isStreaming = it
                    }
                }) {
                    Image(
                        painterResource(if (isStreaming) androidx.core.R.drawable.ic_call_decline else androidx.core.R.drawable.ic_call_answer),
                        contentDescription = "Stream",
                        modifier = Modifier.size(50.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                IconButton(onClick = { selectFile() }) {
                    Image(
                        painterResource(com.pedro.srt.R.drawable.sync_icon),
                        contentDescription = "Select File",
                        modifier = Modifier.size(50.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                IconButton(onClick = { resync() }) {
                    Image(
                        painterResource(androidx.core.R.drawable.ic_call_answer_low),
                        contentDescription = "Resync",
                        modifier = Modifier.size(50.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Seek bar for progress
            Slider(
                value = progress,
                onValueChange = { value -> onSeekBarChange(value) },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red)
            )
        }
    }

    private fun selectFile() {
        activityResult.launch("video/*")
    }

    private fun startStopStream(
        url: String,
        isRecording: Boolean,
        isStreaming: Boolean,
        updateStreamingState: (Boolean) -> Unit
    ) {
        if (genericFromFile.isStreaming) {
            genericFromFile.stopStream()
            updateStreamingState(false)
        } else if (isRecording || prepare()) {
            genericFromFile.startStream(url)
            genericFromFile.setLoopMode(true)
            updateStreamingState(true)
            updateProgress()

        } else {
            toast("Error preparing stream, This device can't do it")
        }
    }


    private fun resync() {
        genericFromFile.reSyncFile()
    }

    private fun onSeekBarChange(value: Float) {
        if (genericFromFile.isStreaming || genericFromFile.isRecording) {
            genericFromFile.moveTo(value.toDouble())
            Handler(Looper.getMainLooper()).postDelayed({ genericFromFile.reSyncFile() }, 500)
        }
    }

    @Throws(IOException::class)
    private fun prepare(): Boolean {
        return filePath?.let { path ->
            genericFromFile.prepareVideo(applicationContext, path) || genericFromFile.prepareAudio(
                applicationContext, path
            )
        } ?: false
    }

    private fun updateProgress() {
        CoroutineScope(Dispatchers.IO).launch {
            while (genericFromFile.isStreaming || genericFromFile.isRecording) {
                delay(1000)
                if (!touching) {
                    withContext(Dispatchers.Main) {
                        val progressValue = max(
                            genericFromFile.videoTime.toInt(), genericFromFile.audioTime.toInt()
                        ).toFloat()
                    }
                }
            }
        }
    }

    override fun onConnectionStarted(url: String) {}
    override fun onConnectionSuccess() {
        toast("Connected")
    }

    override fun onConnectionFailed(reason: String) {
        toast("Failed: $reason")
        genericFromFile.stopStream()
    }

    override fun onNewBitrate(bitrate: Long) {}
    override fun onDisconnect() {
        toast("Disconnected")
    }

    override fun onAuthError() {
        toast("Auth error")
        genericFromFile.stopStream()
    }

    override fun onAuthSuccess() {
        toast("Auth success")
    }

    override fun onVideoDecoderFinished() {}
    override fun onAudioDecoderFinished() {}

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
