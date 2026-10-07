package com.marquee.api.progress;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RatingId implements Serializable {
    @Column(name = "profile_id")
    private Long profileId;

    @Column(name = "title_id")
    private Long titleId;

    protected RatingId() {
    }

    public RatingId(Long profileId, Long titleId) {
        this.profileId = profileId;
        this.titleId = titleId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RatingId other && Objects.equals(profileId, other.profileId) && Objects.equals(titleId, other.titleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profileId, titleId);
    }
}
