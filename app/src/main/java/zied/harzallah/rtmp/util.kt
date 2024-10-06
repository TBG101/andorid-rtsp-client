import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import java.io.File

object PathUtils {
    fun getRecordPath(): File {
        val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        return File(storageDir, "MyAppRecordings").apply {
            if (!exists()) mkdirs()
        }
    }

    fun updateGallery(context: Context, filePath: String) {
        val file = File(filePath)
        val uri = Uri.fromFile(file)
        val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        intent.data = uri
        context.sendBroadcast(intent)
    }
}
