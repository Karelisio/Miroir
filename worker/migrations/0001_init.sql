-- Miroir : le serveur ne stocke que des identifiants opaques et du chiffré (AES-GCM côté client).

CREATE TABLE pairs (
  id               TEXT PRIMARY KEY,
  secret_hash      TEXT NOT NULL,
  rev              INTEGER NOT NULL DEFAULT 1,
  settings         TEXT,
  settings_version INTEGER NOT NULL DEFAULT 0,
  created_at       INTEGER NOT NULL
);

CREATE TABLE members (
  pair_id    TEXT NOT NULL REFERENCES pairs(id) ON DELETE CASCADE,
  member     TEXT NOT NULL CHECK (member IN ('A', 'B')),
  profile    TEXT,
  fcm_token  TEXT,
  joined_at  INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (pair_id, member)
);

-- Question du jour verrouillée : tag = HMAC(clé, idQuestion), ref = idQuestion chiffré.
CREATE TABLE days (
  pair_id     TEXT NOT NULL REFERENCES pairs(id) ON DELETE CASCADE,
  day         TEXT NOT NULL,
  tag         TEXT NOT NULL,
  ref         TEXT NOT NULL,
  skips       INTEGER NOT NULL DEFAULT 0,
  skipped_ref TEXT,
  rev         INTEGER NOT NULL,
  PRIMARY KEY (pair_id, day)
);
CREATE INDEX days_rev ON days (pair_id, rev);

CREATE TABLE answers (
  pair_id    TEXT NOT NULL REFERENCES pairs(id) ON DELETE CASCADE,
  day        TEXT NOT NULL,
  member     TEXT NOT NULL CHECK (member IN ('A', 'B')),
  tag        TEXT NOT NULL,
  payload    TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (pair_id, day, member)
);

CREATE TABLE reactions (
  pair_id    TEXT NOT NULL REFERENCES pairs(id) ON DELETE CASCADE,
  day        TEXT NOT NULL,
  member     TEXT NOT NULL CHECK (member IN ('A', 'B')),
  payload    TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (pair_id, day, member)
);

CREATE TABLE custom_questions (
  pair_id    TEXT NOT NULL REFERENCES pairs(id) ON DELETE CASCADE,
  id         TEXT NOT NULL,
  author     TEXT NOT NULL CHECK (author IN ('A', 'B')),
  payload    TEXT NOT NULL,
  deleted    INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  rev        INTEGER NOT NULL,
  PRIMARY KEY (pair_id, id)
);
CREATE INDEX custom_questions_rev ON custom_questions (pair_id, rev);
