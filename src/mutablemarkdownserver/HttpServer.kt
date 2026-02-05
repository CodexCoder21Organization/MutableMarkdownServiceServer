package mutablemarkdownserver

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Simple HTTP server for the markdown service.
 *
 * Provides a REST-like API for the CLI to interact with:
 *   GET  /health              -> "OK"
 *   GET  /files               -> list all files
 *   GET  /file?id=<uuid>      -> get file by ID
 *   GET  /file?name=<name>    -> get file by name
 *   POST /file                -> create file (body: {name, content})
 *   PUT  /file?id=<uuid>      -> update file (body: {name?, content?})
 *   DELETE /file?id=<uuid>    -> delete file
 */
class MarkdownHttpServer(
    private val rpcHandler: MarkdownRpcHandler,
    private val port: Int
) {
    private var server: HttpServer? = null

    fun start() {
        server = HttpServer.create(InetSocketAddress(port), 0).apply {
            createContext("/", RpcHandler(rpcHandler))
            executor = null
            start()
        }
        println("HTTP server started on port $port")
    }

    fun stop() {
        server?.stop(0)
        println("HTTP server stopped")
    }

    private class RpcHandler(private val rpcHandler: MarkdownRpcHandler) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val path = exchange.requestURI.path.trimEnd('/')
                val query = parseQuery(exchange.requestURI.rawQuery)
                val method = exchange.requestMethod

                val result = when {
                    path == "/health" && method == "GET" -> {
                        rpcHandler.handleP2pRequest("health", emptyMap())
                    }

                    path == "/files" && method == "GET" -> {
                        rpcHandler.handleP2pRequest("getAllFiles", emptyMap())
                    }

                    path == "/file" && method == "GET" -> {
                        when {
                            query.containsKey("id") -> {
                                rpcHandler.handleP2pRequest("getFile", mapOf("id" to query["id"]))
                            }
                            query.containsKey("name") -> {
                                rpcHandler.handleP2pRequest("getFileByName", mapOf("name" to query["name"]))
                            }
                            else -> mapOf("error" to "Must specify 'id' or 'name' parameter")
                        }
                    }

                    path == "/file" && method == "POST" -> {
                        val body = exchange.requestBody.bufferedReader().readText()
                        val json = JSONObject(body)
                        val name = json.optString("name", "")
                        val content = json.optString("content", "")
                        if (name.isEmpty()) {
                            mapOf("error" to "Missing required parameter 'name'")
                        } else {
                            rpcHandler.handleP2pRequest("createFile", mapOf("name" to name, "content" to content))
                        }
                    }

                    path == "/file" && method == "PUT" -> {
                        val id = query["id"]
                        if (id == null) {
                            mapOf("error" to "Must specify 'id' parameter")
                        } else {
                            val body = exchange.requestBody.bufferedReader().readText()
                            val json = JSONObject(body)
                            val results = mutableMapOf<String, Any>()
                            if (json.has("name")) {
                                rpcHandler.handleP2pRequest("setName", mapOf("id" to id, "name" to json.getString("name")))
                                results["nameUpdated"] = true
                            }
                            if (json.has("content")) {
                                rpcHandler.handleP2pRequest("setContent", mapOf("id" to id, "content" to json.getString("content")))
                                results["contentUpdated"] = true
                            }
                            results["ok"] = true
                            results
                        }
                    }

                    path == "/file" && method == "DELETE" -> {
                        val id = query["id"]
                        if (id == null) {
                            mapOf("error" to "Must specify 'id' parameter")
                        } else {
                            rpcHandler.handleP2pRequest("deleteFile", mapOf("id" to id))
                        }
                    }

                    path == "/rpc" && method == "POST" -> {
                        val body = exchange.requestBody.bufferedReader().readText()
                        val json = JSONObject(body)
                        val rpcMethod = json.optString("method", "")
                        val params = mutableMapOf<String, Any?>()
                        if (json.has("params")) {
                            val paramsJson = json.getJSONObject("params")
                            for (key in paramsJson.keys()) {
                                params[key] = paramsJson.get(key)
                            }
                        }
                        rpcHandler.handleP2pRequest(rpcMethod, params)
                    }

                    else -> {
                        mapOf(
                            "service" to "MutableMarkdownServiceServer",
                            "endpoints" to listOf(
                                "GET /health - Health check",
                                "GET /files - List all files",
                                "GET /file?id=<uuid> - Get file by ID",
                                "GET /file?name=<name> - Get file by name",
                                "POST /file - Create file (body: {name, content})",
                                "PUT /file?id=<uuid> - Update file (body: {name?, content?})",
                                "DELETE /file?id=<uuid> - Delete file",
                                "POST /rpc - Raw RPC (body: {method, params})"
                            )
                        )
                    }
                }

                val responseJson = when (result) {
                    null -> JSONObject(mapOf("result" to null))
                    is Map<*, *> -> JSONObject(result)
                    is String -> JSONObject(mapOf("result" to result))
                    else -> JSONObject(mapOf("result" to result.toString()))
                }
                val responseBytes = responseJson.toString().toByteArray(StandardCharsets.UTF_8)

                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, responseBytes.size.toLong())
                exchange.responseBody.use { it.write(responseBytes) }
            } catch (e: Exception) {
                val errorResponse = JSONObject(mapOf("error" to (e.message ?: "Unknown error")))
                val responseBytes = errorResponse.toString().toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(500, responseBytes.size.toLong())
                exchange.responseBody.use { it.write(responseBytes) }
            }
        }

        private fun parseQuery(query: String?): Map<String, String> {
            if (query == null) return emptyMap()
            return query.split("&").mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
                } else null
            }.toMap()
        }
    }
}
