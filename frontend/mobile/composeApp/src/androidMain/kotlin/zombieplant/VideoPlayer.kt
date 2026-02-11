package zombieplant

import android.net.Uri
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.io.FileOutputStream

@Composable
actual fun VideoPlayer(
    data: ByteArray,
    modifier: Modifier
) {
    AndroidView(
        factory = { context ->
            VideoView(context).apply {
                val tempFile = File.createTempFile("video", ".mp4", context.cacheDir)
                val fos = FileOutputStream(tempFile)
                fos.write(data)
                fos.close()
                setVideoURI(Uri.fromFile(tempFile))
                start()
            }
        },
        modifier = modifier
    )
}
