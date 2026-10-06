package com.marquee.api.progress;

public record TitleCardResponse(Long id,
                               String name,
                               String synopsis,
                               String type,
                               String maturityRating,
                               String posterKey,
                               String backdropKey,
                               Integer resumeAt,
                               boolean inMyList) {
}
