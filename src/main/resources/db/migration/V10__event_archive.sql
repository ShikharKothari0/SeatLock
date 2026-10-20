ALTER TABLE event ADD COLUMN archived BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE event ADD CONSTRAINT event_archive_closed CHECK (NOT archived OR publication = 'CLOSED');
