ALTER TABLE collection_photo
    ADD COLUMN cleanup_after TIMESTAMP NULL,
    ADD INDEX idx_collection_photo_retry (state, cleanup_after);
