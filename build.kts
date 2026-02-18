@KotlinBuildScript("https://tools.kotlin.build/")
@file:WithArtifact("kompile:build-kotlin-jvm:0.0.1")
package mutablemarkdownserver

import build.kotlin.withartifact.WithArtifact
import java.io.File
import build.kotlin.jvm.*
import build.kotlin.annotations.MavenArtifactCoordinates

val dependencies = resolveDependencies(
    // Markdown API interfaces
    MavenPrebuilt("community.kotlin.markdown:api:0.0.1"),
    // UrlResolver and UrlProtocol
    MavenPrebuilt("foundation.url:resolver:0.0.352"),
    MavenPrebuilt("foundation.url:protocol:0.0.251"),
    // SJVM for stdlib JAR (needed for bytecode responses)
    MavenPrebuilt("net.javadeploy.sjvm:avianStdlibHelper-jvm:0.0.24"),
    // Clock abstraction (required by UrlProtocol)
    MavenPrebuilt("community.kotlin.clocks.simple:community-kotlin-clocks-simple:0.0.1"),
    // libp2p dependencies
    MavenPrebuilt("io.libp2p:jvm-libp2p:1.2.2-RELEASE"),
    MavenPrebuilt("org.jetbrains.kotlin:kotlin-reflect:1.9.22"),
    MavenPrebuilt("community.kotlin.rpc:protocol-api:0.0.2"),
    MavenPrebuilt("community.kotlin.rpc:protocol-impl:0.0.11"),
    MavenPrebuilt("com.google.protobuf:protobuf-java:3.25.1"),
    MavenPrebuilt("tech.pegasys:noise-java:22.1.0"),
    // JSON
    MavenPrebuilt("org.json:json:20250517"),
    // Kotlin stdlib
    MavenPrebuilt("org.jetbrains.kotlin:kotlin-stdlib:1.9.22"),
    MavenPrebuilt("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22"),
    MavenPrebuilt("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22"),
    // Coroutines
    MavenPrebuilt("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0"),
    // Okio
    MavenPrebuilt("com.squareup.okio:okio-jvm:3.4.0"),
    // Logging
    MavenPrebuilt("org.slf4j:slf4j-api:1.7.36"),
    MavenPrebuilt("org.slf4j:slf4j-simple:1.7.36"),
    // Netty (for libp2p)
    MavenPrebuilt("io.netty:netty-buffer:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-codec:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-codec-http:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-codec-http2:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-common:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-handler:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-resolver:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-transport:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-transport-classes-epoll:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-transport-classes-kqueue:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-transport-native-unix-common:4.1.101.Final"),
    // BouncyCastle
    MavenPrebuilt("org.bouncycastle:bcpkix-jdk18on:1.78.1"),
    MavenPrebuilt("org.bouncycastle:bcprov-jdk18on:1.78.1"),
    MavenPrebuilt("org.bouncycastle:bcutil-jdk18on:1.78.1"),
    // Guava (required by libp2p)
    MavenPrebuilt("com.google.guava:guava:33.2.0-jre"),
    MavenPrebuilt("com.google.guava:failureaccess:1.0.2"),
)

@MavenArtifactCoordinates("community.kotlin.markdown:server:")
fun buildMaven(): File {
    return buildSimpleKotlinMavenArtifact(
        // 0.0.1: Initial release
        //        - MarkdownServiceImpl with file-based storage
        //        - MarkdownRpcHandler for RPC operations
        //        - Support for URL protocol lazy-start mode
        //        - Support for P2P standalone mode
        // 0.0.2: Update foundation.url:resolver to 0.0.293
        // 0.0.3: Fix ServiceHandler/ServiceRegistrationConfig imports (moved to foundation.url.protocol)
        // 0.0.4: Update foundation.url:protocol to 0.0.165 for resolver compatibility
        // 0.0.5: Update foundation.url:resolver to 0.0.295, use new UrlResolver(UrlProtocol2()) API
        // 0.0.6: Update foundation.url:resolver to 0.0.297, foundation.url:protocol to 0.0.218
        // 0.0.12: Upgrade UrlResolver to 0.0.352
        //         - Path-based sandboxed connections: url://markdown/baby-sleep.md now
        //           correctly routes RPC calls with resource path context
        //         - Added MarkdownFile interface methods to client impl for SJVM proxy
        //         - MarkdownRpcHandler supports path-prefixed method dispatch
        // 0.0.11: Upgrade UrlResolver to 0.0.349, UrlProtocol to 0.0.251
        //         - StreamAwareServiceHandler delegation bug fixed in resolver 0.0.349
        // 0.0.10: Downgrade UrlResolver to 0.0.320, UrlProtocol to 0.0.230
        //         - Avoids StreamAwareServiceHandler delegation bug in resolver 0.0.325+
        // 0.0.9: Path-based file lookup: url://markdown/baby-sleep.md returns file data
        //        - RPC methods take precedence over file name lookups
        // 0.0.8: Update UrlResolver to 0.0.328, UrlProtocol to 0.0.234
        // 0.0.7: Add SJVM client library for sandboxed execution
        //        - Pre-compiled client JAR with MarkdownServiceClientImpl
        //        - MarkdownFileImpl with RPC-backed mutable properties
        //        - BytecodeGenerator for serving client bytecode
        //        - __bytecode_request RPC method with stdlibJar support
        coordinates = "community.kotlin.markdown:server:0.0.12",
        src = File("src"),
        compileDependencies = dependencies
    )
}

fun buildSkinnyJar(): File {
    return buildMaven().jar
}

// Client code dependencies - implements interfaces from MarkdownApi
val clientDependencies = resolveDependencies(
    MavenPrebuilt("org.jetbrains.kotlin:kotlin-stdlib:1.9.22"),
    MavenPrebuilt("community.kotlin.markdown:api:0.0.1"),
    MavenPrebuilt("foundation.url:service-bridge-stub:0.0.1"),
)

/**
 * Build the client JAR for SJVM execution.
 */
fun buildClientJar(): File {
    val artifact = buildSimpleKotlinMavenArtifact(
        // 0.0.1: Initial release
        //        - MarkdownServiceClientImpl for RPC calls
        //        - MarkdownFileImpl with RPC-backed mutable properties
        coordinates = "community.kotlin.markdown:server-client:0.0.1",
        src = File("src-client"),
        compileDependencies = clientDependencies
    )
    return artifact.jar
}

/**
 * Build a FAT client JAR that includes all dependencies.
 */
fun buildClientFatJar(): File {
    val skinnyJar = buildClientJar()
    return BuildJar(null, clientDependencies.map { it.jar } + skinnyJar)
}

/**
 * Creates a JAR containing the client FAT JAR as a resource entry /client-impl.jar.
 */
fun buildClientResourcesJar(): File {
    val clientFatJar = buildClientFatJar()
    val tempFile = java.io.File.createTempFile("client-resources", ".jar")
    java.util.jar.JarOutputStream(tempFile.outputStream()).use { jos ->
        val entry = java.util.jar.JarEntry("client-impl.jar")
        jos.putNextEntry(entry)
        jos.write(clientFatJar.readBytes())
        jos.closeEntry()
    }
    return tempFile
}

/**
 * Build fat JAR with client included.
 */
fun buildFatJar(): File {
    val manifest = Manifest("mutablemarkdownserver.MainKt")
    val clientResourcesJar = buildClientResourcesJar()
    return BuildJar(manifest, dependencies.map { it.jar } + buildSkinnyJar() + clientResourcesJar)
}
