package mutablemarkdownserver

import community.kotlin.markdown.api.MarkdownFile
import community.kotlin.markdown.api.MarkdownService
import org.json.JSONObject
import java.io.File
import java.util.*

/**
 * Server-side markdown file storage implementation.
 *
 * Stores markdown files as JSON files on disk. Each file is stored
 * as a JSON file named by its UUID under the files/ subdirectory.
 */
class MarkdownServiceImpl(private val location: File) : MarkdownService {

    override fun createFile(name: String, content: String): MarkdownFile {
        val file = MarkdownFileImpl(this, UUID.randomUUID())
        file.name = name
        file.content = content
        return file
    }

    override fun getAllFiles(): Collection<MarkdownFile> {
        val filesDir = filesDir()
        println("[MarkdownService] getAllFiles: looking in ${filesDir.absolutePath}")
        val files = filesDir.listFiles()
        println("[MarkdownService] getAllFiles: found ${files?.size ?: 0} files")
        if (files == null) return emptyList()
        return files.mapNotNull { file ->
            try {
                MarkdownFileImpl(this, UUID.fromString(file.name))
            } catch (e: IllegalArgumentException) {
                println("[MarkdownService] Skipping invalid file: ${file.name}")
                null
            }
        }
    }

    override fun getFile(id: UUID): MarkdownFile {
        val fileData = File(filesDir(), id.toString())
        if (!fileData.exists()) {
            throw IllegalArgumentException(
                "Markdown file with id $id does not exist. " +
                "Use getAllFiles() to see available files."
            )
        }
        return MarkdownFileImpl(this, id)
    }

    override fun getFileByName(name: String): MarkdownFile? {
        return getAllFiles().find { it.name == name }
    }

    override fun deleteFile(file: MarkdownFile) {
        val fileData = File(filesDir(), file.id.toString())
        if (fileData.exists()) {
            fileData.delete()
            println("[MarkdownService] Deleted file: ${file.id}")
        }
    }

    internal fun filesDir(): File = File(location, "files").apply { mkdirs() }
}

/**
 * Server-side implementation of MarkdownFile.
 *
 * Stores file data as JSON with properties: name, content, lastModified.
 */
class MarkdownFileImpl(
    private val service: MarkdownServiceImpl,
    override val id: UUID
) : MarkdownFile {

    private val file = File(service.filesDir(), id.toString()).also {
        if (!it.exists()) {
            it.writeText("{}")
            println("[MarkdownFile] Created file: ${it.absolutePath}")
        }
    }

    override var name: String
        get() = config.optString("name", "")
        set(value) { writeConfig(name = value) }

    override var content: String
        get() = config.optString("content", "")
        set(value) { writeConfig(content = value) }

    override val lastModified: Long
        get() = config.optLong("lastModified", 0L)

    private val config: JSONObject
        get() {
            if (!file.exists()) file.writeText("{}")
            return JSONObject(file.readText())
        }

    private fun writeConfig(
        name: String = this.name,
        content: String = this.content
    ) {
        val root = JSONObject()
        root.put("name", name)
        root.put("content", content)
        root.put("lastModified", System.currentTimeMillis())
        file.writeText(root.toString())
    }

    override fun equals(other: Any?): Boolean {
        if (other !is MarkdownFileImpl) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
