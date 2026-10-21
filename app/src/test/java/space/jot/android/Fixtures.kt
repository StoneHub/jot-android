package space.jot.android

/** Reads a test fixture from src/test/resources. */
object Fixtures {
    fun bytes(path: String): ByteArray = checkNotNull(Fixtures::class.java.classLoader?.getResource(path)) { "Missing fixture $path" }.readBytes()
    fun text(path: String): String = bytes(path).decodeToString()
}
