package com.marquee.api.progress;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class MyListId implements Serializable {
    private Long profileId;
    private Long titleId;

    public MyListId() {
    }

    public MyListId(Long profileId, Long titleId) {
        this.profileId = profileId;
        this.titleId = titleId;
    }

    public Long getProfileId() {
        return profileId;
    }

    public void setProfileId(Long profileId) {
        this.profileId = profileId;
    }

    public Long getTitleId() {
        return titleId;
    }

    public void setTitleId(Long titleId) {
        this.titleId = titleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MyListId myListId = (MyListId) o;
        return Objects.equals(profileId, myListId.profileId) && Objects.equals(titleId, myListId.titleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profileId, titleId);
    }
}
