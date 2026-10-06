package com.marquee.api.progress;

import com.marquee.api.catalog.Title;
import com.marquee.api.profile.Profile;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "my_list")
public class MyList {
    @EmbeddedId
    private MyListId id;

    @MapsId("profileId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @MapsId("titleId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "title_id", nullable = false)
    private Title title;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt = Instant.now();

    protected MyList() {
    }

    public MyList(Profile profile, Title title) {
        this.profile = profile;
        this.title = title;
        this.id = new MyListId(profile.getId(), title.getId());
    }

    public MyListId getId() {
        return id;
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public Title getTitle() {
        return title;
    }

    public void setTitle(Title title) {
        this.title = title;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
