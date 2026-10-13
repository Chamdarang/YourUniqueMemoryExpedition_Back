ALTER TABLE spot_purchase
    ADD COLUMN received_place_name VARCHAR(200) NULL,
    ADD COLUMN goshuin_book_id BIGINT NULL,
    ADD COLUMN goshuin_order INT NULL,
    ADD COLUMN photo_id VARCHAR(36) NULL,
    ADD COLUMN record_version BIGINT NOT NULL DEFAULT 0,
    MODIFY COLUMN note VARCHAR(2000) NULL;

UPDATE spot_purchase p LEFT JOIN spot_user su ON su.id = p.spot_user_id
LEFT JOIN spot s ON s.id = su.spot_id
SET p.received_place_name = COALESCE(NULLIF(su.custom_name, ''), s.spot_name)
WHERE p.kind = 'GOSHUIN';

CREATE TABLE goshuin_book (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    memo VARCHAR(2000) NULL,
    photo_id VARCHAR(36) NULL,
    source_purchase_id BIGINT NULL UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_goshuin_book_user (user_id, created_at),
    CONSTRAINT fk_goshuin_book_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_goshuin_book_purchase FOREIGN KEY (source_purchase_id) REFERENCES spot_purchase(id) ON DELETE SET NULL
);

ALTER TABLE spot_purchase
    ADD INDEX idx_goshuin_purchase_book (user_id, goshuin_book_id, goshuin_order),
    ADD CONSTRAINT fk_goshuin_purchase_book FOREIGN KEY (goshuin_book_id) REFERENCES goshuin_book(id) ON DELETE SET NULL;

-- Keep deletion jobs after account deletion so private files are also removed.
CREATE TABLE collection_photo (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    storage VARCHAR(10) NOT NULL,
    original_key VARCHAR(200) NOT NULL,
    thumbnail_key VARCHAR(200) NOT NULL,
    content_type VARCHAR(40) NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    byte_size BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_collection_photo_cleanup (state, created_at)
);
