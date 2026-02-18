package mutablemarkdownserver

import community.kotlin.markdown.api.MarkdownFile
import community.kotlin.markdown.api.MarkdownFileImpl
import foundation.url.sjvm.intrinsics.ServiceBridge
import java.util.UUID

/**
 * Client-side implementation of MarkdownService that runs inside SJVM.
 *
 * Methods dispatch to the server via ServiceBridge.rpc(), which SJVM intercepts
 * and routes to the host's RPC handler.
 */
class MarkdownServiceClientImpl {

    fun createFile(name: String, content: String): MarkdownFile {
        val result = ServiceBridge.rpc("createFile", mapOf(
            "name" to name,
            "content" to content
        ))
        val id = UUID.fromString(result["id"].toString())
        val lastModified = (result["lastModified"] as Number).toLong()
        return MarkdownFileImpl(id, name, content, lastModified)
    }

    fun getAllFiles(): Collection<MarkdownFile> {
        val result = ServiceBridge.rpc("getAllFiles", emptyMap())
        @Suppress("UNCHECKED_CAST")
        val files = result["files"] as? List<Map<String, Any?>> ?: return emptyList()
        return files.map { fileMap ->
            val id = UUID.fromString(fileMap["id"].toString())
            val name = fileMap["name"].toString()
            val lastModified = (fileMap["lastModified"] as Number).toLong()
            MarkdownFileImpl(id, name, "", lastModified)
        }
    }

    fun getFile(id: UUID): MarkdownFile {
        val result = ServiceBridge.rpc("getFile", mapOf("id" to id.toString()))
        val name = result["name"].toString()
        val content = result["content"].toString()
        val lastModified = (result["lastModified"] as Number).toLong()
        return MarkdownFileImpl(id, name, content, lastModified)
    }

    fun getFileByName(name: String): MarkdownFile? {
        val result = ServiceBridge.rpc("getFileByName", mapOf("name" to name))
        val found = result["found"]
        if (found == false) {
            return null
        }
        val id = UUID.fromString(result["id"].toString())
        val content = result["content"].toString()
        val lastModified = (result["lastModified"] as Number).toLong()
        return MarkdownFileImpl(id, name, content, lastModified)
    }

    fun deleteFile(file: MarkdownFile) {
        ServiceBridge.rpc("deleteFile", mapOf("id" to file.id.toString()))
    }

    // MarkdownFile interface methods for path-based lookup (url://markdown/baby-sleep.md).
    // When opened via a path-based URL, the server resolves the file from the URL path
    // and injects the file id into params before dispatching.

    fun getId(): UUID {
        val result = ServiceBridge.rpc("getId", emptyMap<String, Any>())
        return UUID.fromString(result["id"].toString())
    }

    fun getName(): String {
        val result = ServiceBridge.rpc("getName", emptyMap<String, Any>())
        return result["name"].toString()
    }

    fun setName(value: String) {
        ServiceBridge.rpc("setName", mapOf("name" to value))
    }

    fun getContent(): String {
        val result = ServiceBridge.rpc("getContent", emptyMap<String, Any>())
        return result["content"].toString()
    }

    fun setContent(value: String) {
        ServiceBridge.rpc("setContent", mapOf("content" to value))
    }

    fun getLastModified(): Long {
        val result = ServiceBridge.rpc("getLastModified", emptyMap<String, Any>())
        return (result["lastModified"] as Number).toLong()
    }
}
