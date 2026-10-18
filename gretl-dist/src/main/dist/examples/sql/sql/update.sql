CREATE TABLE IF NOT EXISTS gretl_beispiel (
    id integer PRIMARY KEY,
    beschreibung text NOT NULL
);

INSERT INTO gretl_beispiel (id, beschreibung)
VALUES (1, 'Beispiel erfolgreich ausgeführt')
ON CONFLICT (id) DO UPDATE
    SET beschreibung = EXCLUDED.beschreibung;
