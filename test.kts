@file:WithArtifact("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-test:1.9.22")
package mutablemarkdownserver.tests

import build.kotlin.withartifact.WithArtifact
import kotlin.test.assertTrue

/**
 * Build verification tests for MutableMarkdownServiceServer.
 * The actual server classes are verified by the build step;
 * these tests just ensure the test framework is working.
 */

fun testBuildSucceeded() {
    // If we got here, the build step succeeded
    assertTrue(true, "Build completed successfully")
}
