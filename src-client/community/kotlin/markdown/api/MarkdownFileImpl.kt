package community.kotlin.markdown.api

import foundation.url.sjvm.intrinsics.ServiceBridge
import java.util.UUID

/**
 * Client-side implementation of MarkdownFile that runs inside SJVM.
 *
 * The id and lastModified properties are immutable. The name and content
 * properties are mutable and use RPC calls to get/set values on the server.
 */
class MarkdownFileImpl(
    override val id: UUID,
    private var _name: String,
    private var _content: String,
    override val lastModified: Long
) : MarkdownFile {

    override var name: String
        get() {
            val result = ServiceBridge.rpc("getName", mapOf("id" to id.toString()))
            return result["name"].toString()
        }
        set(value) {
            ServiceBridge.rpc("setName", mapOf("id" to id.toString(), "name" to value))
        }

    override var content: String
        get() {
            val result = ServiceBridge.rpc("getContent", mapOf("id" to id.toString()))
            return result["content"].toString()
        }
        set(value) {
            ServiceBridge.rpc("setContent", mapOf("id" to id.toString(), "content" to value))
        }
}
