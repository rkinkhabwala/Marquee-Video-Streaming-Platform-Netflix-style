-- JPA binds Java enums (and the maturity rating converter) as VARCHAR, which native
-- Postgres enum columns reject. Store them as VARCHAR and keep validation via CHECK.

ALTER TABLE users ALTER COLUMN role DROP DEFAULT;
ALTER TABLE users ALTER COLUMN role TYPE VARCHAR(16) USING role::text;
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'USER';
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'));

ALTER TABLE titles ALTER COLUMN type TYPE VARCHAR(16) USING type::text;
ALTER TABLE titles ADD CONSTRAINT chk_titles_type CHECK (type IN ('MOVIE', 'SERIES'));

ALTER TABLE titles ALTER COLUMN maturity_rating TYPE VARCHAR(8) USING maturity_rating::text;
ALTER TABLE titles ADD CONSTRAINT chk_titles_maturity_rating CHECK (maturity_rating IN
    ('G', 'PG', 'TV-Y', 'TV-Y7', 'TV-G', 'TV-PG', 'PG-13', 'R', 'NC-17', 'TV-14', 'TV-MA'));

ALTER TABLE video_assets ALTER COLUMN status DROP DEFAULT;
ALTER TABLE video_assets ALTER COLUMN status TYPE VARCHAR(16) USING status::text;
ALTER TABLE video_assets ALTER COLUMN status SET DEFAULT 'UPLOADED';
ALTER TABLE video_assets ADD CONSTRAINT chk_video_assets_status CHECK (status IN ('UPLOADED', 'TRANSCODING', 'READY', 'FAILED'));

ALTER TABLE transcode_jobs ALTER COLUMN status DROP DEFAULT;
ALTER TABLE transcode_jobs ALTER COLUMN status TYPE VARCHAR(16) USING status::text;
ALTER TABLE transcode_jobs ALTER COLUMN status SET DEFAULT 'QUEUED';
ALTER TABLE transcode_jobs ADD CONSTRAINT chk_transcode_jobs_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED'));

DROP TYPE user_role;
DROP TYPE title_type;
DROP TYPE maturity_rating;
DROP TYPE video_asset_status;
DROP TYPE transcode_status;
