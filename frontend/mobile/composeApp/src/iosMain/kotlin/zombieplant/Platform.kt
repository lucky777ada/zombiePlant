
package zombieplant

import kotlin.system.getTimeMillis

class IOSPlatform : Platform {
    override val name: String = "iOS"
    override fun currentTimeMillis(): Long {
        return getTimeMillis()
    }
}
