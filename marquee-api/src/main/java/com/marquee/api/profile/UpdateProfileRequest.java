package com.marquee.api.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(min = 1, max = 100) String name,
        String avatarKey,
        Boolean isKids) {
}
