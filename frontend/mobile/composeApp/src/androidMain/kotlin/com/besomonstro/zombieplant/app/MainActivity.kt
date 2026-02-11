package com.besomonstro.zombieplant.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import ui.ZombiePlantTheme
import zombieplant.AndroidPlatform
import zombieplant.ZombiePlantDashboard
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            ZombiePlantTheme {
                ZombiePlantDashboard(
                    videoPlayer = { videoData, onClose ->
                        VideoPlayer(
                            videoData = videoData,
                            onClose = onClose
                        )
                    },
                    platform = AndroidPlatform()
                )
            }
        }
    }
}

@Composable
fun VideoPlayer(videoData: ByteArray?, onClose: () -> Unit) {
    if (videoData == null) {
        onClose()
        return
    }

    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val tempFile = File(context.cacheDir, "video.mp4")
            tempFile.writeBytes(videoData)
            val mediaItem = MediaItem.fromUri(Uri.fromFile(tempFile))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))) {
        DisposableEffect(Unit) {
            onDispose {
                exoPlayer.release()
            }
        }
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }
    }
}
