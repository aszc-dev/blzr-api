-- Content-addressable patch storage for blzr.app
-- Phase 3: Backend + Short URLs

CREATE TABLE IF NOT EXISTS patches (
  id              TEXT PRIMARY KEY,           -- 8-char base62 short ID
  structure_hash  TEXT NOT NULL,              -- topology fingerprint (from audio/hash)
  params_hash     TEXT NOT NULL,              -- deterministic param hash
  full_hash       TEXT NOT NULL,              -- structure + params combined
  descriptor      TEXT NOT NULL,              -- full Transit+JSON blob
  name            TEXT,                       -- user-facing name (optional)
  description     TEXT,                       -- user-facing description (optional)
  source          TEXT NOT NULL DEFAULT 'user', -- builtin | user | community
  node_count      INTEGER NOT NULL DEFAULT 0,
  edge_count      INTEGER NOT NULL DEFAULT 0,
  created_at      TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at      TEXT NOT NULL DEFAULT (datetime('now')),
  deleted_at      TEXT                        -- soft delete
);

CREATE INDEX IF NOT EXISTS idx_patches_structure ON patches(structure_hash);
CREATE INDEX IF NOT EXISTS idx_patches_full      ON patches(full_hash);
CREATE INDEX IF NOT EXISTS idx_patches_source    ON patches(source);
CREATE INDEX IF NOT EXISTS idx_patches_created   ON patches(created_at);

CREATE TABLE IF NOT EXISTS modules (
  id             TEXT PRIMARY KEY,            -- UUID
  patch_id       TEXT NOT NULL REFERENCES patches(id),
  name           TEXT NOT NULL,
  category       TEXT NOT NULL,               -- utility | modulation | voice | user
  library_desc   TEXT,
  source         TEXT NOT NULL DEFAULT 'user',
  display_order  INTEGER NOT NULL DEFAULT 0,
  created_at     TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_modules_source   ON modules(source);
CREATE INDEX IF NOT EXISTS idx_modules_category ON modules(category);

-- Schema versioning
CREATE TABLE IF NOT EXISTS schema_version (
  version INTEGER NOT NULL
);

INSERT INTO schema_version (version) VALUES (1);
