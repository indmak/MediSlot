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
   - the **KB Q&A page** (`/admin/knowledge/ask`) — admins, KB maintainers;
   - **pre-consultation grounding** — relevant snippets are appended to the AI's
     system prompt.

## Sources

Documents are grouped by **source** (`knowledge_source`), managed at
`/admin/knowledge/sources`:

| Source | Type | Content | Visibility |
|--------|------|---------|------------|
| 人工上传 | `MANUAL` | files uploaded by a maintainer | `PUBLIC` |
| 诊前咨询记录 | `CONSULTATION` | Markdown auto-generated from closed consultation sessions | `PRIVATE` |
| 外部知识接口 | `EXTERNAL_API` | third-party REST API batches (next phase) | `PUBLIC` |

The three sources are seeded idempotently on startup.

### Consultation → Markdown (source 2)

`ConsultationKbService` renders a closed group session into Markdown: visit info,
structured symptom intake, the group transcript, **doctor-approved** AI drafts,
diagnosis note, and the AI summary. Directives and unapproved drafts are excluded.

- **Trigger**: a scheduled sweep every 5 minutes (`KnowledgeSyncScheduler`, toggle
  `kb.consultation.auto-sync`) plus a **立即同步** button on the sources page.
- **Idempotency**: `conversation.kb_document_id` marks a session already archived;
  content hashes de-duplicate identical documents.
- **Anonymisation**: the patient's name and phone number are stripped (also from
  message bodies); the doctor, department and clinical content are kept.
- **Threshold**: a session needs at least `kb.consultation.min-messages` messages,
  a summary, a diagnosis note, or an approved draft.

## Visibility & privacy

Each vector chunk carries a `visibility` metadata value. Retrieval is filtered:

- **Patient-side grounding** (`RagService.contextForConsultation`) adds
  `visibility == 'PUBLIC'`, so consultation-derived (private) documents can never
  leak into another patient's session.
- **KB Q&A** (`/admin/knowledge/ask`, admins/maintainers) retrieves everything.

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
| `kb.consultation.auto-sync` | admin settings | auto-archive closed sessions (scheduled sweep) |
| `kb.consultation.min-messages` | admin settings | minimum messages before a session is archived |

## Secret

```
secrets/medislot/external/zhipu/apikey   ->  medislot.external.zhipu.apikey
```

The app container runs as uid 1001; keep the secret file owned by that uid.

## Re-indexing

Changing the embedding model or chunking requires re-embedding. Use the
**重建索引** button per document (or delete + re-upload). Vectors for a document
are removed by metadata filter `documentId == '<id>'` before re-adding.
