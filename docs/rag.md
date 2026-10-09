# Knowledge Base & RAG

MediSlot ships a retrieval-augmented generation (RAG) knowledge base that the
pre-consultation AI and the KB Q&A page can use.

## Stack

| Piece | Choice |
|-------|--------|
| Orchestration | Spring AI 2.0 (`VectorStore`, `TikaDocumentReader`, `TokenTextSplitter`) |
| Vector store | PostgreSQL 16 + **pgvector** (same database as business data) |
| Embeddings | **Zhipu `embedding-3`** (1024 dims, OpenAI-compatible) |
| Chat / generation | DeepSeek (existing client) |
| Doc parsing | Apache Tika (PDF / Word / Markdown / txt) |

## How it works

1. A **KB maintainer** (or admin) uploads a document at `/admin/knowledge`.
2. The file is stored on the mounted volume (`medislot.kb.dir`, `/app/uploads/kb`).
3. It is parsed with Tika, split into ~500-token chunks, embedded via Zhipu, and
   added to the `vector_store` table (metadata: `documentId`, `title`, `chunkIndex`).
4. Retrieval (`RagService`) is used in two places:
   - the **KB Q&A page** (`/admin/knowledge/ask`) — admins, KB maintainers, doctors;
   - **pre-consultation grounding** — relevant snippets are appended to the AI's
     system prompt.

## Configuration

| Key | Where | Meaning |
|-----|-------|---------|
| `medislot.rag.enabled` | config | enables the vector store beans (off in tests) |
| `medislot.rag.dimensions` | config | must match the embedding model (1024) |
| `medislot.external.zhipu.apikey` | mounted secret | Zhipu API key |
| `medislot.external.zhipu.model` | config | default `embedding-3` |
| `rag.enabled` | admin settings | toggle consultation grounding |
| `rag.top-k` | admin settings | number of retrieved chunks |
| `rag.max-context-chars` | admin settings | cap on injected context size |

## Secret

```
secrets/medislot/external/zhipu/apikey   ->  medislot.external.zhipu.apikey
```

The app container runs as uid 1001; keep the secret file owned by that uid.

## Re-indexing

Changing the embedding model or chunking requires re-embedding. Use the
**重建索引** button per document (or delete + re-upload). Vectors for a document
are removed by metadata filter `documentId == '<id>'` before re-adding.
