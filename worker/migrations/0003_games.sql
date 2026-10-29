-- De l'autre côté : parties, réponses, réglages, envies et questions perso. Tout le contenu est chiffré
-- côté client ; le serveur ne voit que des identifiants opaques et applique les règles de révélation.

CREATE TABLE game_sessions (
  pair_id    TEXT NOT NULL REFERENCES pairs(id) ON DELETE CASCADE,
  id         TEXT NOT NULL,
  kind       TEXT NOT NULL,
  -- item : réponse de l'autre visible une fois qu'on a répondu au même élément ;
  -- all : visible une fois que les deux ont terminé ; open : tout est visible.
  reveal     TEXT NOT NULL CHECK (reveal IN ('item', 'all', 'open')),
  created_by TEXT NOT NULL CHECK (created_by IN ('A', 'B')),
  payload    TEXT NOT NULL,
  status     TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'done', 'cancelled')),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  rev        INTEGER NOT NULL,
  PRIMARY KEY (pair_id, id)
);
CREATE INDEX game_sessions_rev ON game_sessions (pair_id, rev);

-- item « $done » : partie terminée par ce membre ; items commençant par « ~ » : toujours visibles.
CREATE TABLE game_answers (
  pair_id    TEXT NOT NULL REFERENCES pairs(id) ON DELETE CASCADE,
  session_id TEXT NOT NULL,
  member     TEXT NOT NULL CHECK (member IN ('A', 'B')),
  item       TEXT NOT NULL,
  payload    TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (pair_id, session_id, member, item)
);

-- Réglages de jeu de chaque membre (intensité, catégories exclues), lisibles par les deux.
CREATE TABLE game_members (
  pair_id    TEXT NOT NULL REFERENCES pairs(id) ON DELETE CASCADE,
  member     TEXT NOT NULL CHECK (member IN ('A', 'B')),
  settings   TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  rev        INTEGER NOT NULL,
  PRIMARY KEY (pair_id, member)
);

-- Envies communes : par élément, deux étiquettes HMAC aveugles (t1, t2) ; le serveur ne renvoie à
-- chacun que les étiquettes communes aux deux membres. « mine » (chiffré) n'est renvoyé qu'à son auteur.
CREATE TABLE game_wishes (
  pair_id    TEXT NOT NULL REFERENCES pairs(id) ON DELETE CASCADE,
  member     TEXT NOT NULL CHECK (member IN ('A', 'B')),
  slot       TEXT NOT NULL,
  t1         TEXT NOT NULL,
  t2         TEXT NOT NULL,
  mine       TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  rev        INTEGER NOT NULL,
  PRIMARY KEY (pair_id, member, slot)
);
CREATE INDEX game_wishes_rev ON game_wishes (pair_id, member, rev);

CREATE TABLE game_custom (
  pair_id    TEXT NOT NULL REFERENCES pairs(id) ON DELETE CASCADE,
  id         TEXT NOT NULL,
  author     TEXT NOT NULL CHECK (author IN ('A', 'B')),
  payload    TEXT NOT NULL,
  deleted    INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  rev        INTEGER NOT NULL,
  PRIMARY KEY (pair_id, id)
);
CREATE INDEX game_custom_rev ON game_custom (pair_id, rev);
