package mutablemarkdownserver

import foundation.url.protocol.Libp2pRpcProtocol
import java.util.*

/**
 * Handles RPC requests for the markdown service.
 *
 * RPC Methods:
 *   health           -> "OK"
 *   __bytecode_request -> Base64 JAR + class name + stdlibJar
 *   createFile       -> creates a file, returns its UUID
 *   getAllFiles      -> returns list of file metadata (id, name, lastModified)
 *   getFile          -> returns file data (id, name, content, lastModified)
 *   getFileByName    -> returns file data by name, or null
 *   deleteFile       -> deletes a file by UUID
 *   setName          -> sets file name
 *   getName          -> gets file name
 *   setContent       -> sets file content
 *   getContent       -> gets file content
 *   getLastModified  -> gets file last modified timestamp
 */
class MarkdownRpcHandler(
    private val markdownService: MarkdownServiceImpl,
    private val jarBytes: ByteArray,
    private val implClassName: String,
    private val stdlibJarBytes: ByteArray
) {

    fun handleRequest(request: Libp2pRpcProtocol.RpcRequest): Libp2pRpcProtocol.RpcResponse {
        return try {
            val result = dispatch(request.method, request.params)
            Libp2pRpcProtocol.RpcResponse.success(request.id, result)
        } catch (e: Exception) {
            println("[MarkdownServiceServer] Error handling '${request.method}': ${e.message}")
            Libp2pRpcProtocol.RpcResponse.error(request.id, "-1", e.message ?: "Unknown error")
        }
    }

    fun handleP2pRequest(path: String, params: Map<String, Any?>): Any? {
        // Support path-prefixed methods for path-based connections (e.g., "baby-sleep.md/getName").
        // When a client opens url://markdown/baby-sleep.md, subsequent RPC calls arrive with
        // the file path prepended to the method name. We resolve the file by name and inject
        // its id into the params so the method handler can operate on the correct file.
        val slashIndex = path.indexOf('/')
        if (slashIndex > 0) {
            val filePath = path.substring(0, slashIndex)
            val method = path.substring(slashIndex + 1)
            val file = markdownService.getFileByName(filePath)
            if (file != null) {
                val augmentedParams = params.toMutableMap()
                augmentedParams["id"] = file.id.toString()
                return dispatch(method, augmentedParams)
            }
        }
        return dispatch(path, params)
    }

    private fun dispatch(method: String, params: Map<String, Any?>): Any? {
        return when (method) {
            "health" -> "OK"

            "__bytecode_request" -> {
                println("[MarkdownServiceServer] Serving bytecode (impl: ${jarBytes.size} bytes, stdlib: ${stdlibJarBytes.size} bytes)")
                mapOf(
                    "jar" to Base64.getEncoder().encodeToString(jarBytes),
                    "className" to implClassName,
                    "stdlibJar" to Base64.getEncoder().encodeToString(stdlibJarBytes)
                )
            }

            "createFile" -> {
                val name = requireParam(params, "name")
                val content = params["content"]?.toString() ?: ""
                val file = markdownService.createFile(name, content)
                mapOf(
                    "id" to file.id.toString(),
                    "name" to file.name,
                    "lastModified" to file.lastModified
                )
            }

            "getAllFiles" -> {
                val files = markdownService.getAllFiles()
                mapOf("files" to files.map { file ->
                    mapOf(
                        "id" to file.id.toString(),
                        "name" to file.name,
                        "lastModified" to file.lastModified
                    )
                })
            }

            "getFile" -> {
                val id = UUID.fromString(requireParam(params, "id"))
                val file = markdownService.getFile(id)
                fileToMap(file)
            }

            "getFileByName" -> {
                val name = requireParam(params, "name")
                val file = markdownService.getFileByName(name)
                if (file != null) fileToMap(file) else mapOf("found" to false)
            }

            "deleteFile" -> {
                val id = UUID.fromString(requireParam(params, "id"))
                val file = markdownService.getFile(id)
                markdownService.deleteFile(file)
                mapOf("deleted" to true)
            }

            "setName" -> {
                val id = UUID.fromString(requireParam(params, "id"))
                val name = requireParam(params, "name")
                markdownService.getFile(id).name = name
                mapOf("ok" to true)
            }

            "getName" -> {
                val id = UUID.fromString(requireParam(params, "id"))
                mapOf("name" to markdownService.getFile(id).name)
            }

            "setContent" -> {
                val id = UUID.fromString(requireParam(params, "id"))
                val content = requireParam(params, "content")
                markdownService.getFile(id).content = content
                mapOf("ok" to true)
            }

            "getContent" -> {
                val id = UUID.fromString(requireParam(params, "id"))
                mapOf("content" to markdownService.getFile(id).content)
            }

            "getId" -> {
                val id = requireParam(params, "id")
                mapOf("id" to id)
            }

            "getLastModified" -> {
                val id = UUID.fromString(requireParam(params, "id"))
                mapOf("lastModified" to markdownService.getFile(id).lastModified)
            }

            else -> {
                // Try to look up as a file by name (supports url://markdown/baby-sleep.md style requests)
                val fileByName = markdownService.getFileByName(method)
                if (fileByName != null) {
                    println("[MarkdownServiceServer] Resolved path '$method' to file ${fileByName.id}")
                    fileToMap(fileByName)
                } else {
                    mapOf(
                        "service" to "url://markdown/",
                        "type" to "rpc",
                        "description" to "Mutable markdown file storage service",
                        "availableMethods" to listOf(
                            "health: returns 'OK'",
                            "__bytecode_request: returns client bytecode for SJVM sandbox execution",
                            "createFile(name, content?): returns {id, name, lastModified}",
                            "getAllFiles(): returns {files: [{id, name, lastModified}, ...]}",
                            "getFile(id): returns {id, name, content, lastModified}",
                            "getFileByName(name): returns file or {found: false}",
                            "deleteFile(id): returns {deleted: true}",
                            "setName(id, name): returns {ok: true}",
                            "getName(id): returns {name}",
                            "setContent(id, content): returns {ok: true}",
                            "getContent(id): returns {content}",
                            "getLastModified(id): returns {lastModified}",
                            "<filename>: returns file data if a file with that name exists"
                        )
                    )
                }
            }
        }
    }

    private fun fileToMap(file: community.kotlin.markdown.api.MarkdownFile): Map<String, Any> {
        return mapOf(
            "id" to file.id.toString(),
            "name" to file.name,
            "content" to file.content,
            "lastModified" to file.lastModified,
            "found" to true
        )
    }
}

private fun requireParam(params: Map<String, Any?>, name: String): String {
    return params[name]?.toString()
        ?: throw IllegalArgumentException(
            "Missing required parameter '$name'. Provided parameters: ${params.keys}"
        )
}
