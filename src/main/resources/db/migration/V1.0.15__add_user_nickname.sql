ALTER TABLE users ADD COLUMN nickname VARCHAR(100) NULL AFTER username;

UPDATE users
SET nickname = username
WHERE nickname IS NULL;

ALTER TABLE users MODIFY COLUMN nickname VARCHAR(100) NOT NULL;
