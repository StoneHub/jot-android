package space.jot.android

/** Reads a test fixture from src/test/resources as UTF-8. */
object Fixtures {
    fun text(path: String): String = checkNotNull(Fixtures::class.java.classLoader?.getResource(path)) { "Missing fixture $path" }.readText()
}
