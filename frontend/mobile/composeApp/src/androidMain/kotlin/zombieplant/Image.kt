
package zombieplant

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

@Composable
actual fun Image(
    data: ByteArray,
    modifier: Modifier,
    contentDescription: String?
) {
    val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size).asImageBitmap()
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}
