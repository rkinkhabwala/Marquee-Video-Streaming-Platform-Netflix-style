package com.marquee.api.progress;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Thumbs up ({@code 1}) or down ({@code -1}) from a profile for a title. */
@Entity
@Table(name = "ratings")
public class Rating {
    @EmbeddedId
    private RatingId id;

    @Column(name = "value", nullable = false)
    private short thumbs;

    protected Rating() {
    }

    public Rating(Long profileId, Long titleId, int thumbs) {
        this.id = new RatingId(profileId, titleId);
        this.thumbs = (short) thumbs;
    }

    public int getThumbs() {
        return thumbs;
    }

    public void setThumbs(int thumbs) {
        this.thumbs = (short) thumbs;
    }
}
