ALTER TABLE users ADD COLUMN refresh_token_hash VARCHAR(512);
CREATE INDEX idx_users_refresh_token_hash ON users(refresh_token_hash);
