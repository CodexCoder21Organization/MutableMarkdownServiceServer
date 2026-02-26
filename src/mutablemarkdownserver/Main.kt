package mutablemarkdownserver

import foundation.url.protocol.*
import foundation.url.resolver.UrlProtocol2
import java.io.File

/**
 * Loads the SJVM stdlib JAR for sandbox execution.
 */
private fun loadStdlibJar(): ByteArray {
    val resourceStream = object {}.javaClass.getResourceAsStream("/stdlib.jar")
    if (resourceStream != null) {
        println("Loading SJVM stdlib JAR from /stdlib.jar")
        return resourceStream.use { it.readBytes() }
    }
    throw IllegalStateException(
        "Cannot find SJVM stdlib JAR at /stdlib.jar. Ensure net.javadeploy.sjvm:avianStdlibHelper-jvm is available on the classpath."
    )
}

/**
 * Loads the pre-compiled client JAR bytes.
 */
private fun loadClientJar(): ByteArray {
    // Try classpath resource first
    val resourceStream = object {}.javaClass.getResourceAsStream("/client-impl.jar")
    if (resourceStream != null) {
        println("Loading client JAR from classpath resource")
        return resourceStream.use { it.readBytes() }
    }

    // Try environment variable
    val envPath = System.getenv("CLIENT_JAR_PATH")
    if (envPath != null) {
        val file = File(envPath)
        if (file.exists()) {
            println("Loading client JAR from $envPath")
            return file.readBytes()
        }
    }

    // Try current directory
    val localFile = File("client-impl.jar")
    if (localFile.exists()) {
        println("Loading client JAR from ./client-impl.jar")
        return localFile.readBytes()
    }

    throw IllegalStateException(
        "Cannot find client implementation JAR. Ensure client-impl.jar is available via:\n" +
        "  - Classpath resource /client-impl.jar\n" +
        "  - Environment variable CLIENT_JAR_PATH\n" +
        "  - File ./client-impl.jar in current directory"
    )
}

/**
 * url://markdown/ Service Provider
 *
 * A URL service that provides mutable markdown file storage. The server
 * stores markdown files in a configurable directory and exposes them via
 * RPC methods for CRUD operations.
 *
 * Operating modes (in priority order):
 *   1. HTTP mode: Set HTTP_PORT to run a simple HTTP server for CLI access
 *   2. URL lazy-start mode: Set URL_BIND_DOMAIN for ContainerNursery deployments
 *   3. P2P mode: Default if no environment variables are set
 *
 * Environment variables:
 *   MARKDOWN_SERVICE_DATA_DIR - Storage location (default: /root/markdown-service-data)
 *   HTTP_PORT - Port for HTTP server mode (e.g., "8080")
 *   URL_BIND_DOMAIN - Bind domain for lazy-start mode (e.g., "markdown.example.com:${PORT}")
 *   PORT - Port number (used to substitute ${PORT} in URL_BIND_DOMAIN)
 */
fun main() {
    println("=== url://markdown/ Service Provider ===")
    println()
    println("This service provides mutable markdown file storage.")
    println()

    // Load the pre-compiled client JAR
    val clientJarBytes = loadClientJar()
    BytecodeGenerator.clientJarBytes = clientJarBytes

    // Load the SJVM stdlib JAR for sandbox execution
    val stdlibJarBytes = loadStdlibJar()
    println("Loaded SJVM stdlib JAR: ${stdlibJarBytes.size} bytes")

    // Use environment variable or fall back to a known persistent location
    val dataDir = System.getenv("MARKDOWN_SERVICE_DATA_DIR")?.let { File(it) }
        ?: File("/root/markdown-service-data")

    println("Storage location: ${dataDir.absolutePath}")
    if (!dataDir.exists()) {
        dataDir.mkdirs()
        println("Created storage directory")
    }

    val markdownService = MarkdownServiceImpl(dataDir)

    val implementationJar = BytecodeGenerator.generateImplementationJar()
    val implementationClassName = BytecodeGenerator.getImplementationClassName()
    println("Generated implementation JAR: ${implementationJar.size} bytes")
    println("Implementation class: $implementationClassName")

    val rpcHandler = MarkdownRpcHandler(markdownService, implementationJar, implementationClassName, stdlibJarBytes)

    // Check for HTTP mode first (simplest for CLI access)
    val httpPort = System.getenv("HTTP_PORT")?.toIntOrNull()
    if (httpPort != null) {
        println("Running in HTTP mode on port $httpPort")
        runHttpMode(httpPort, rpcHandler)
        return
    }

    // Check for URL lazy-start mode
    val rawBindDomain = System.getenv(UrlProtocol.ENV_BIND_DOMAIN)
    val port = System.getenv("PORT")
    val bindDomain = when {
        rawBindDomain == null -> null
        rawBindDomain.contains("\${PORT}") && port != null -> rawBindDomain.replace("\${PORT}", port)
        else -> rawBindDomain
    }

    if (bindDomain != null) {
        println("Running in lazy-start mode with URL_BIND_DOMAIN=$bindDomain")
        runUrlMode(bindDomain, rpcHandler, implementationJar, implementationClassName, stdlibJarBytes)
    } else {
        println("Running in standalone P2P mode")
        runP2pMode(rpcHandler, implementationJar, implementationClassName, stdlibJarBytes)
    }
}

/**
 * Run with simple HTTP server for CLI access.
 */
private fun runHttpMode(port: Int, rpcHandler: MarkdownRpcHandler) {
    val httpServer = MarkdownHttpServer(rpcHandler, port)
    httpServer.start()

    println()
    println("HTTP server started!")
    println("  URL: http://localhost:$port")
    println()
    println("Endpoints:")
    println("  GET  /health             - Health check")
    println("  GET  /files              - List all files")
    println("  GET  /file?id=<uuid>     - Get file by ID")
    println("  GET  /file?name=<name>   - Get file by name")
    println("  POST /file               - Create file (body: {name, content})")
    println("  PUT  /file?id=<uuid>     - Update file (body: {name?, content?})")
    println("  DELETE /file?id=<uuid>   - Delete file")
    println("  POST /rpc                - Raw RPC (body: {method, params})")
    println()
    println("Press Ctrl+C to stop.")

    Runtime.getRuntime().addShutdownHook(Thread {
        println()
        println("Shutting down HTTP server...")
        httpServer.stop()
        println("Goodbye!")
    })

    Thread.currentThread().join()
}

/**
 * Run with URL protocol for lazy-start containers.
 */
private fun runUrlMode(
    bindDomain: String,
    rpcHandler: MarkdownRpcHandler,
    jarBytes: ByteArray,
    implClassName: String,
    stdlibJarBytes: ByteArray
) {
    val protocol = UrlProtocol()

    val handler: suspend (Libp2pRpcProtocol.RpcRequest, EffectPropagator) -> Libp2pRpcProtocol.RpcResponse = { request, _ ->
        println("[MarkdownServiceServer] URL request: method='${request.method}', params=${request.params}")
        rpcHandler.handleRequest(request)
    }

    val serviceUrl = "url://$bindDomain/"
    println("Binding url://markdown/ service to $serviceUrl...")
    val binding = protocol.bind(serviceUrl, handler)

    println()
    println("Service bound successfully!")
    println("  Service identifier: ${binding.serviceIdentifier}")
    println("  Active: ${binding.isActive}")
    println()
    println("RPC methods available: health, __bytecode_request, createFile, getAllFiles, getFile,")
    println("  getFileByName, deleteFile, setName, getName, setContent, getContent, getLastModified")
    println()
    println("Press Ctrl+C to stop.")

    Runtime.getRuntime().addShutdownHook(Thread {
        println()
        println("Shutting down URL service...")
        binding.stop()
        protocol.close()
        println("Goodbye!")
    })

    Thread.currentThread().join()
}

/**
 * Run with full P2P networking for standalone mode.
 */
private fun runP2pMode(
    rpcHandler: MarkdownRpcHandler,
    jarBytes: ByteArray,
    implClassName: String,
    stdlibJarBytes: ByteArray
) {
    val resolver = foundation.url.resolver.UrlResolver(UrlProtocol2())

    val handler = object : foundation.url.protocol.ServiceHandler {
        override suspend fun handleRequest(
            path: String,
            params: Map<String, Any?>,
            metadata: Map<String, String>
        ): Any? {
            println("[MarkdownServiceServer] P2P request: path='$path', params=$params")
            return rpcHandler.handleP2pRequest(path, params, metadata)
        }

        override fun getImplementationJar(): ByteArray = jarBytes
        override fun getImplementationClassName(): String = implClassName

        override fun onShutdown() {
            println("[MarkdownServiceServer] P2P service shutting down")
        }
    }

    println("Registering url://markdown/ service with P2P network...")
    val registration = resolver.registerGlobalService(
        serviceUrl = "url://markdown/",
        handler = handler,
        config = foundation.url.protocol.ServiceRegistrationConfig(
            metadata = mapOf(
                "description" to "Mutable markdown file storage service",
                "type" to "rpc"
            ),
            reannounceIntervalMs = 5 * 60 * 1000
        )
    )

    println()
    println("Service registered successfully!")
    println("  Peer ID: ${registration.peerId}")
    println("  Multiaddresses: ${registration.multiaddresses.joinToString(", ")}")
    println("  Service URL: url://markdown/")
    println()
    println("RPC methods available: health, __bytecode_request, createFile, getAllFiles, getFile,")
    println("  getFileByName, deleteFile, setName, getName, setContent, getContent, getLastModified")
    println()
    println("Press Ctrl+C to stop.")

    Runtime.getRuntime().addShutdownHook(Thread {
        println()
        println("Shutting down P2P service...")
        registration.unregister()
        resolver.close()
        println("Goodbye!")
    })

    Thread.currentThread().join()
}
