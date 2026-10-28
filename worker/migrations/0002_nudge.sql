-- Bouton « Coucou » : anti-spam (un coucou toutes les 30 minutes au plus par membre).
ALTER TABLE members ADD COLUMN last_nudge_at INTEGER;
