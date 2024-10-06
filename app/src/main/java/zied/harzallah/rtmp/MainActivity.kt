package zied.harzallah.rtmp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.gl.render.filters.BlackFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import com.pedro.library.rtsp.RtspCamera1
import com.pedro.library.view.OpenGlView
import zied.harzallah.rtmp.ui.theme.RtmpTheme


class MainActivity : ComponentActivity(), ConnectChecker, SurfaceHolder.Callback {

    private lateinit var rtmpCamera1: RtspCamera1

    private val rtmpUrl = "rtsp://192.168.1.19:8554/live"
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private var streamingImage = false

    private val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
    )

    // Registering the permission result callback
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
            } else {
                // Handle permission denial
                Log.e("MainActivity", "Permissions denied")
            }
        }

    private fun allPermissionsGranted(): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        if (!allPermissionsGranted()) {
            requestPermissionLauncher.launch(permissions)
        } else {
            // Start the camera stream if permissions are already granted
            // startCameraStream()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check and request permissions
        requestPermissions()

        // Register video picker result
        imagePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val data: Intent? = result.data
                val imageUri: Uri? = data?.data
                imageUri?.let { uri ->
                    streamImage(uri)
                } ?: run {
                    Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
                }
            }



        setContent {
            RtmpTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    CameraPreviewView()
                }
            }
        }
    }


    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        imagePickerLauncher.launch(intent)
    }

    private fun startCameraStream() {
        if (!rtmpCamera1.isStreaming) {
            if (rtmpCamera1.prepareAudio() && rtmpCamera1.prepareVideo()) {
                rtmpCamera1.startStream(rtmpUrl)
            } else {
                Toast.makeText(this, "Error preparing stream", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun streamImage(uri: Uri) {
        if (streamingImage) {
            rtmpCamera1.glInterface.clearFilters()
            streamingImage = false
            return
        }

        streamingImage = true
        val bitmap = BitmapFactory.decodeResource(
            resources, R.drawable.dog
        ) // Your image resource
        val imageObjectFilterRender = ImageObjectFilterRender()
        rtmpCamera1.glInterface.setFilter(BlackFilterRender())
        rtmpCamera1.glInterface.setFilter(imageObjectFilterRender)
        imageObjectFilterRender.apply {
            setImage(bitmap)
        }


    }

    private fun streamSelectedVideo(videoUri: Uri) {
        mediaPlayer = MediaPlayer.create(this, videoUri).apply {
            setSurface(rtmpCamera1.glInterface.surface)
            setOnCompletionListener {
                stopMediaPlayer()
            }
            start()
        }

        if (!rtmpCamera1.isStreaming) {
            rtmpCamera1.prepareAudio()
            rtmpCamera1.prepareVideo()
            rtmpCamera1.startStream(rtmpUrl)
        }
    }

    private fun stopStream() {
        if (rtmpCamera1.isStreaming) {
            rtmpCamera1.stopStream()
        }
        stopMediaPlayer()
    }

    private fun stopMediaPlayer() {
        if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            mediaPlayer.release()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        rtmpCamera1.startPreview()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (rtmpCamera1.isStreaming) {
            rtmpCamera1.stopStream()
        }
        rtmpCamera1.stopPreview()
        stopMediaPlayer()
    }

    override fun onAuthError() {
        runOnUiThread { Toast.makeText(this, "Auth Error", Toast.LENGTH_SHORT).show() }
    }

    override fun onAuthSuccess() {
        runOnUiThread { Toast.makeText(this, "Auth Success", Toast.LENGTH_SHORT).show() }
    }

    override fun onConnectionFailed(reason: String) {
        runOnUiThread {
            Toast.makeText(this, "Connection Failed: $reason", Toast.LENGTH_SHORT).show()
        }
        Log.e("failed trmp", reason)
        rtmpCamera1.stopStream()
        stopMediaPlayer()
    }

    override fun onConnectionStarted(url: String) {
        runOnUiThread {
            Toast.makeText(this, "Connection Started: $url", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionSuccess() {
        runOnUiThread { Toast.makeText(this, "Connection Success", Toast.LENGTH_SHORT).show() }
    }

    override fun onDisconnect() {
        runOnUiThread {
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
        }
    }


    @Composable
    fun PercentageHeight(percentage: Float, content: @Composable () -> Unit) {
        val screenHeight = LocalContext.current.resources.displayMetrics.heightPixels
        val height = (screenHeight * percentage).toInt() // Calculate height in pixels

        Box(modifier = Modifier.height(height.dp)) {
            content()
        }
    }

    @Composable
    fun CameraPreviewView() {
        var streamingImage by remember { mutableStateOf(false) }
        var isStreaming by remember { mutableStateOf(false) }
        val context = LocalContext.current

        Column(
            modifier = Modifier.fillMaxSize().fillMaxHeight(), // Add padding for a cleaner layout
            verticalArrangement = Arrangement.Top, // Space between preview and buttons
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Camera Preview taking a fixed height
            PercentageHeight(percentage = 0.25f) {
                AndroidView(
                    factory = { context ->
                        OpenGlView(context).apply {
                            rtmpCamera1 = RtspCamera1(this, this@MainActivity)

                            holder.addCallback(this@MainActivity)
                        }
                    }, modifier = Modifier.fillMaxWidth()
                    // Set a fixed height for the camera preview
                )
            }

            PercentageHeight(percentage = 0.3f) {

                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                        .padding(horizontal = 10.dp), // Full width for buttons
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Button to Start Camera Stream
                    Button(
                        onClick = {
                            if (!isStreaming) startCameraStream()
                            else stopStream()
                        },
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 8.dp) // Padding for button
                    ) {
                        if (!isStreaming) Text(text = "Stop Stream")
                        else Text(text = "Start Stream")
                    }

                    // Button to Pick and Stream Selected Video
                    Button(
                        onClick = {
                            val intent = Intent(context, FromFileActivity::class.java)
                            context.startActivity(intent)
                        }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text(text = "Pick Video")
                    }

                    // Button to Pick Image
                    Button(
                        onClick = {
                            pickImage()

                        }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text(text = if (!streamingImage) "Pick an Image" else "Stop Rendering Image")
                    }
                }
            }
        }
    }


}


