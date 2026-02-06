@file:WithArtifact("community.kotlin.markdown:server:0.0.1")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-test:1.9.22")
package mutablemarkdownserver.tests

import build.kotlin.withartifact.WithArtifact
import mutablemarkdownserver.MarkdownServiceImpl
import mutablemarkdownserver.MarkdownRpcHandler
import mutablemarkdownserver.MarkdownHttpServer
import kotlin.test.assertNotNull

/**
 * Smoke tests for MutableMarkdownServiceServer.
 * These verify the server classes are properly compiled and accessible.
 */

fun testMarkdownServiceImplIsAccessible() {
    val clazz = MarkdownServiceImpl::class
    assertNotNull(clazz, "MarkdownServiceImpl class should exist")
}

fun testMarkdownRpcHandlerIsAccessible() {
    val clazz = MarkdownRpcHandler::class
    assertNotNull(clazz, "MarkdownRpcHandler class should exist")
}

fun testMarkdownHttpServerIsAccessible() {
    val clazz = MarkdownHttpServer::class
    assertNotNull(clazz, "MarkdownHttpServer class should exist")
}
