
package zombieplant

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun VideoPlayer(
    data: ByteArray,
    modifier: Modifier = Modifier
)
