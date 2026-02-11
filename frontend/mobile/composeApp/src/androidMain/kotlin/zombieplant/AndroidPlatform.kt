
package zombieplant

class AndroidPlatform : Platform {
    override fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }
}
