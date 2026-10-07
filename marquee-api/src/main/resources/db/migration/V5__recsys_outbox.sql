-- Transactional outbox for the recommendation system: rows are written in the same transaction as
-- the change they describe and delivered by RecsysOutboxRelay, so recsys being down never fails or
-- slows a Marquee request, and nothing is lost while it is down.
CREATE TABLE recsys_outbox (
    id BIGSERIAL PRIMARY KEY,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('EVENT', 'CATALOG', 'USER_DELETE')),
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    attempts INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_recsys_outbox_kind_created ON recsys_outbox(kind, created_at);

-- Highest progress milestone (0/25/50/75/100) already reported for the current viewing.
ALTER TABLE watch_progress ADD COLUMN milestone SMALLINT NOT NULL DEFAULT 0;
