# blzr-api

Content-addressable patch storage API for [blzr.app](https://blzr.app).

Babashka HTTP service with EDN file persistence. Stores synthesizer patch descriptors (Transit+JSON) and provides short URLs for sharing.

## Prerequisites

- [Babashka](https://github.com/babashka/babashka) v1.12+

## Quick start

```bash
# Start the server (port 3001, data at ./blzr-data.edn)
bb server

# Or with custom config
PORT=8080 DB_PATH=/tmp/blzr-data.edn bb server
```

## API

**Base URL:** `https://api.blzr.app`

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

## Storage

Data is stored in a single EDN file (default: `/data/blzr-data.edn` in Docker, `./blzr-data.edn` locally). Thread-safe via locking. The file is created automatically on first startup.

```edn
{:patches {"aB3xK9mQ" {:id "aB3xK9mQ" :structure-hash "..." :descriptor "..." ...}}
 :modules {"uuid" {:id "uuid" :patch-id "aB3xK9mQ" :name "..." ...}}}
```

## Deployment

Docker image based on `babashka/babashka:latest`.

```bash
docker build -t blzr-api .
docker run -p 3001:3001 -v blzr-api-data:/data blzr-api
```

### Coolify

Deployed via Dockerfile build on Coolify (self-hosted at `37.60.247.171`).

- **App UUID:** `w8ogwo8gcsokgc4ckgw4co0c`
- **Port:** 3001
- **Health check:** `GET /health`
- **Volume:** `blzr-api-data:/data` (persistent)
- **Domain:** `https://api.blzr.app` (TLS via Traefik)

## CORS

Allowed origins:
- `https://blzr.app` (production)
- `http://localhost:3000` (development)

## Architecture

```
request → router (pattern matching) → handler → db (EDN file)
                                     → Transit encode/decode
                                     → djb2 hashing (content addressing)
```

All hashing is server-side — the client sends raw descriptors, the server computes structure/params/full hashes for indexing and deduplication.

No external dependencies beyond Babashka builtins (httpkit, transit). Custom router and CORS middleware avoid JVM-only libraries.

## License

MIT
