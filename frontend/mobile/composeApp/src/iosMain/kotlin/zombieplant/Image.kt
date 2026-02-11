
package zombieplant

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

@Composable
actual fun Image(
    data: ByteArray,
    modifier: Modifier,
    contentDescription: String?
) {
    val image = Image.makeFromEncoded(data).toComposeImageBitmap()
    Image(
        bitmap = image,
        contentDescription = contentDescription,
        modifier = modifier
    )
}
