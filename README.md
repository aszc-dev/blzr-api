# blzr-api

Content-addressable patch storage API for [blzr.app](https://blzr.app).

Babashka HTTP service with SQLite persistence. Stores synthesizer patch descriptors (Transit+JSON) and provides short URLs for sharing.

## Prerequisites

- [Babashka](https://github.com/babashka/babashka) v1.12+
- [curl](https://curl.se/) (for healthcheck in Docker)

## Quick start

```bash
# Start the server (port 3001, SQLite at ./blzr.db)
bb server

# Or with custom config
PORT=8080 DB_PATH=/tmp/blzr.db bb server
```

## API

### Patches

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/patches` | Store a patch descriptor. Returns `{id, url}`. Deduplicates by full hash. |
| `GET` | `/api/patches/:id` | Fetch full Transit descriptor. |
| `GET` | `/api/patches/:id/meta` | Fetch metadata only (name, node count, dates). |
| `GET` | `/api/structures/:hash` | Find all patches sharing a topology hash. |

### Modules

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/modules` | Save a module (linked to a patch). |
| `GET` | `/api/modules` | List modules. Filter with `?source=builtin\|user`. |

### Health

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/health` | Returns `{"status":"ok"}`. |

### Example: publish a patch

```bash
curl -X POST https://api.blzr.app/api/patches \
  -H "Content-Type: application/transit+json" \
  -d '<transit+json descriptor>'

# Response (201):
# {"id":"aB3xK9mQ","url":"https://blzr.app/p/aB3xK9mQ"}
```

### Example: fetch a patch

```bash
curl https://api.blzr.app/api/patches/aB3xK9mQ \
  -H "Accept: application/transit+json"

# Response (200): full Transit descriptor
```

## Content addressing

Patches are hashed at three levels:

- **Structure hash** — topology fingerprint (node types + edge connections). Isomorphic graphs produce identical hashes.
- **Params hash** — deterministic hash of all parameter values, sorted canonically.
- **Full hash** — combined structure + params. Used for deduplication: publishing the same patch twice returns the existing short ID.

## Deployment

Docker image based on `babashka/babashka:latest`. SQLite database is stored at `DB_PATH` (default `/data/blzr.db`) — mount a persistent volume.

```bash
docker build -t blzr-api .
docker run -p 3001:3001 -v blzr-data:/data blzr-api
```

### Coolify

Deployed via Dockerfile build. Configure:
- **Port:** 3001
- **Health check:** `GET /health`
- **Volume:** `/data` (persistent, for SQLite)
- **Domain:** `api.blzr.app`

## CORS

Allowed origins:
- `https://blzr.app` (production)
- `http://localhost:3000` (development)

## Architecture

```
request → reitit router → handler → db (next.jdbc) → SQLite
                                   → Transit encode/decode
                                   → djb2 hashing (content addressing)
```

All hashing is server-side — the client sends raw descriptors, the server computes structure/params/full hashes for indexing and deduplication.

## License

MIT
