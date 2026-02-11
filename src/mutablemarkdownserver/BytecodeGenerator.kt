package mutablemarkdownserver

/**
 * Generates the implementation JAR that will be loaded by clients.
 * The client code is pre-compiled and bundled with the server.
 */
object BytecodeGenerator {
    /** Pre-compiled client JAR bytes, loaded at startup */
    lateinit var clientJarBytes: ByteArray

    /**
     * Returns the implementation JAR bytes for the client.
     */
    fun generateImplementationJar(): ByteArray {
        return clientJarBytes
    }

    /**
     * Returns the fully qualified class name of the implementation.
     * This class implements MarkdownService and is loaded into the client's SJVM.
     */
    fun getImplementationClassName(): String {
        val envClass = System.getenv("CLIENT_IMPL_CLASS")
        if (envClass != null) {
            return envClass
        }
        return "mutablemarkdownserver/MarkdownServiceClientImpl"
    }
}
