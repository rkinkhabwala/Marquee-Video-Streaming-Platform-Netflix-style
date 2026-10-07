package com.marquee.api.recsys;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "recsys_outbox")
public class OutboxEntry {
    public enum Kind {
        /** payload: a {@link RecsysEvent} as JSON */
        EVENT,
        /** payload: the title id; the relay sends the title's current state (or deletes it) */
        CATALOG,
        /** payload: the profile id whose recsys data must be erased */
        USER_DELETE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private int attempts;

    protected OutboxEntry() {
    }

    public OutboxEntry(Kind kind, String payload) {
        this.kind = kind;
        this.payload = payload;
    }

    public Long getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void recordFailedAttempt() {
        attempts++;
    }
}
