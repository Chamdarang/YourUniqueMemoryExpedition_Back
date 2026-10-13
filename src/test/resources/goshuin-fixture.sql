CREATE TABLE users(id BIGINT PRIMARY KEY);
CREATE TABLE spot(id BIGINT PRIMARY KEY, spot_name VARCHAR(200));
CREATE TABLE spot_user(id BIGINT PRIMARY KEY,user_id BIGINT,spot_id BIGINT,custom_name VARCHAR(200));
CREATE TABLE spot_purchase(
 id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,spot_user_id BIGINT,
 kind VARCHAR(30),status VARCHAR(20),quantity INT,category VARCHAR(40),price DECIMAL(13,2),currency CHAR(3),
 item_name VARCHAR(200),acquired_date DATE,note VARCHAR(2000),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,received_place_name VARCHAR(200),goshuin_book_id BIGINT,
 goshuin_order INT,photo_id VARCHAR(36),record_version BIGINT DEFAULT 0);
CREATE TABLE goshuin_book(
 id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT,name VARCHAR(80),memo VARCHAR(2000),photo_id VARCHAR(36),
 source_purchase_id BIGINT UNIQUE,version BIGINT DEFAULT 0,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(source_purchase_id) REFERENCES spot_purchase(id) ON DELETE SET NULL);
ALTER TABLE spot_purchase ADD FOREIGN KEY(goshuin_book_id) REFERENCES goshuin_book(id) ON DELETE SET NULL;
CREATE TABLE collection_photo(
 id VARCHAR(36) PRIMARY KEY,user_id BIGINT,storage VARCHAR(10),original_key VARCHAR(200),thumbnail_key VARCHAR(200),
 content_type VARCHAR(40),width INT,height INT,byte_size BIGINT,state VARCHAR(20),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,cleanup_after TIMESTAMP);
