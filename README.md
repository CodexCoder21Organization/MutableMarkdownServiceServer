# MutableMarkdownServiceServer

Server implementation for the MutableMarkdown service, providing persistent storage for markdown files via URL protocol.

## Overview

MutableMarkdownServiceServer stores markdown files in a configurable directory and exposes them via RPC methods. It supports two operating modes:

1. **Lazy-start mode** - Binds to a TCP port for ContainerNursery deployments
2. **Standalone P2P mode** - Uses UrlResolver for peer-to-peer networking

## Building

```bash
# Build fat JAR (executable)
./scripts/build.bash mutablemarkdownserver.buildFatJar output.jar

# Build Maven artifact
./scripts/build.bash mutablemarkdownserver.buildMaven

# Launch directly
./scripts/build.bash --launch mutablemarkdownserver.buildFatJar
```

## Running

### Running the Fat JAR

```bash
java -jar mutable-markdown-server.jar
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MARKDOWN_SERVICE_DATA_DIR` | Storage directory for markdown files | `/root/markdown-service-data` |
| `URL_BIND_DOMAIN` | Bind domain for lazy-start mode | (P2P mode if not set) |
| `PORT` | Port number (substitutes `${PORT}` in URL_BIND_DOMAIN) | - |

### Example: ContainerNursery Deployment

```bash
URL_BIND_DOMAIN="markdown.example.com:${PORT}" java -jar mutable-markdown-server.jar
```

### Example: Local Development

```bash
MARKDOWN_SERVICE_DATA_DIR=/tmp/markdown-data java -jar mutable-markdown-server.jar
```

## Path-Based File Access

Files can be accessed directly by name through the URL path. This works with both raw RPC requests and sandboxed connections.

### Sandboxed Connection (Recommended)

Use `openSandboxedConnection` with a path-based URL to get a typed `MarkdownFile` proxy:

```kotlin
import foundation.url.resolver.UrlResolver

val resolver = UrlResolver()
val file: MarkdownFile = resolver.openSandboxedConnection(
    "url://markdown/baby-sleep.md",
    MarkdownFile::class
)

println(file.name)       // "baby-sleep.md"
println(file.content)    // "# Baby Sleep Tips\n\nSome content here."
file.content = "# Updated Content"  // Mutates the file on the server
```

The SJVM sandbox executes the client implementation locally, routing RPC calls through the persistent P2P connection. The server automatically resolves the resource path ("baby-sleep.md") and injects the file ID into each RPC call.

### Raw RPC

Path-based URLs also work with raw RPC. Requesting `url://markdown/baby-sleep.md` as a path will return the file data (id, name, content, lastModified). Named RPC methods (e.g., `health`, `getAllFiles`) always take precedence over file name lookups.

## RPC Methods

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `health` | none | `"OK"` | Health check |
| `createFile` | `name`, `content?` | `{id, name, lastModified}` | Create a new file |
| `getAllFiles` | none | `{files: [{id, name, lastModified}, ...]}` | List all files |
| `getFile` | `id` | `{id, name, content, lastModified}` | Get file by UUID |
| `getFileByName` | `name` | file data or `{found: false}` | Get file by name |
| `deleteFile` | `id` | `{deleted: true}` | Delete a file |
| `setName` | `id`, `name` | `{ok: true}` | Update file name |
| `getName` | `id` | `{name}` | Get file name |
| `setContent` | `id`, `content` | `{ok: true}` | Update file content |
| `getContent` | `id` | `{content}` | Get file content |
| `getId` | `id` | `{id}` | Echo file UUID (used by path-based connections) |
| `getLastModified` | `id` | `{lastModified}` | Get modification timestamp |
| `<filename>` | none | file data or service metadata | Get file by path name |

## Data Storage

Files are stored as JSON in the configured data directory:

```
markdown-service-data/
└── files/
    ├── <uuid-1>
    ├── <uuid-2>
    └── <uuid-3>
```

Each file contains:
```json
{
  "name": "readme.md",
  "content": "# Hello World",
  "lastModified": 1706889600000
}
```

## Maven Coordinates

```
community.kotlin.markdown:server:0.0.12
```

## Related Projects

- **MutableMarkdownApi** - Interface definitions used by this server
- **MutableMarkdownCli** - Command-line tool for interacting with this server
- **TaskServiceServer** - Similar architecture, used as inspiration for this project
