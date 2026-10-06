CREATE TYPE user_role AS ENUM ('USER', 'ADMIN');
CREATE TYPE title_type AS ENUM ('MOVIE', 'SERIES');
CREATE TYPE maturity_rating AS ENUM (
    'G',
    'PG',
    'TV-Y',
    'TV-Y7',
    'TV-G',
    'TV-PG',
    'PG-13',
    'R',
    'NC-17',
    'TV-14',
    'TV-MA'
);
CREATE TYPE video_asset_status AS ENUM ('UPLOADED', 'TRANSCODING', 'READY', 'FAILED');
CREATE TYPE transcode_status AS ENUM ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED');

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role user_role NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    avatar_key TEXT,
    is_kids BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_profiles_user_id ON profiles(user_id);

CREATE TABLE titles (
    id BIGSERIAL PRIMARY KEY,
    type title_type NOT NULL,
    name VARCHAR(255) NOT NULL,
    synopsis TEXT,
    release_year INTEGER,
    maturity_rating maturity_rating,
    poster_key TEXT,
    backdrop_key TEXT,
    search_vector TSVECTOR,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_titles_type_published ON titles(type, published, created_at DESC);
CREATE INDEX idx_titles_search_vector ON titles USING GIN (search_vector);

CREATE TABLE genres (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE title_genres (
    title_id BIGINT NOT NULL REFERENCES titles(id) ON DELETE CASCADE,
    genre_id BIGINT NOT NULL REFERENCES genres(id) ON DELETE CASCADE,
    PRIMARY KEY (title_id, genre_id)
);

CREATE INDEX idx_title_genres_genre_id ON title_genres(genre_id);

CREATE TABLE seasons (
    id BIGSERIAL PRIMARY KEY,
    title_id BIGINT NOT NULL REFERENCES titles(id) ON DELETE CASCADE,
    season_number INTEGER NOT NULL,
    name VARCHAR(255) NOT NULL,
    UNIQUE (title_id, season_number)
);

CREATE INDEX idx_seasons_title_id ON seasons(title_id);

CREATE TABLE video_assets (
    id BIGSERIAL PRIMARY KEY,
    title_id BIGINT REFERENCES titles(id) ON DELETE SET NULL,
    status video_asset_status NOT NULL DEFAULT 'UPLOADED',
    duration_seconds INTEGER,
    source_key TEXT,
    master_playlist_key TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_video_assets_title_id ON video_assets(title_id);
CREATE INDEX idx_video_assets_status ON video_assets(status);

CREATE TABLE episodes (
    id BIGSERIAL PRIMARY KEY,
    season_id BIGINT NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
    episode_number INTEGER NOT NULL,
    name VARCHAR(255) NOT NULL,
    synopsis TEXT,
    video_asset_id BIGINT UNIQUE REFERENCES video_assets(id) ON DELETE SET NULL,
    UNIQUE (season_id, episode_number)
);

CREATE INDEX idx_episodes_season_id_episode_number ON episodes(season_id, episode_number);

CREATE TABLE transcode_jobs (
    id BIGSERIAL PRIMARY KEY,
    video_asset_id BIGINT NOT NULL REFERENCES video_assets(id) ON DELETE CASCADE,
    attempt INTEGER NOT NULL DEFAULT 0,
    status transcode_status NOT NULL DEFAULT 'QUEUED',
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    log_tail TEXT
);

CREATE INDEX idx_transcode_jobs_asset_status ON transcode_jobs(video_asset_id, status);

CREATE TABLE watch_progress (
    profile_id BIGINT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    video_asset_id BIGINT NOT NULL REFERENCES video_assets(id) ON DELETE CASCADE,
    position_seconds INTEGER NOT NULL DEFAULT 0,
    duration_seconds INTEGER NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (profile_id, video_asset_id)
);

CREATE INDEX idx_watch_progress_updated_at ON watch_progress(updated_at DESC);

CREATE TABLE my_list (
    profile_id BIGINT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    title_id BIGINT NOT NULL REFERENCES titles(id) ON DELETE CASCADE,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (profile_id, title_id)
);

CREATE INDEX idx_my_list_added_at ON my_list(added_at DESC);

CREATE TABLE ratings (
    profile_id BIGINT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    title_id BIGINT NOT NULL REFERENCES titles(id) ON DELETE CASCADE,
    value SMALLINT NOT NULL CHECK (value IN (-1, 1)),
    PRIMARY KEY (profile_id, title_id)
);
