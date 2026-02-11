
package zombieplant

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerLayer
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView
import platform.darwin.NSObject
import kotlin.random.Random

@Composable
actual fun VideoPlayer(
    data: ByteArray,
    modifier: Modifier
) {
    val player = remember {
        val tempFile = NSURL(fileURLWithPath = NSTemporaryDirectory() + "video${Random.nextInt()}.mp4")
        val nsData = data.toNSData()
        nsData.writeToURL(tempFile, true)
        AVPlayer(uRL = tempFile)
    }
    val playerLayer = remember { AVPlayerLayer().apply { setPlayer(player) } }
    val playerViewController = remember { AVPlayerViewController().apply { this.player = player } }

    UIKitView(
        factory = {
            playerViewController.view
        },
        modifier = modifier,
        onResize = { view, rect ->
            CATransaction.begin()
            CATransaction.setValue(true, kCATransactionDisableActions)
            view.layer.frame = rect
            playerLayer.frame = rect
            CATransaction.commit()
        },
    )
}

private fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = this@toNSData.refTo(0), length = this@toNSData.size.toULong())
}
