
package zombieplant.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun Image(
    data: ByteArray,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
)
