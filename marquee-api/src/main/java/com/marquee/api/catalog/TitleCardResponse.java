package com.marquee.api.catalog;

public record TitleCardResponse(Long id,
                               String name,
                               String synopsis,
                               String type,
                               String maturityRating,
                               String posterKey,
                               String backdropKey,
                               Integer resumeAt,
                               Long resumeAssetId,
                               boolean inMyList) {
    public static TitleCardResponse of(Title title, boolean inMyList) {
        return of(title, inMyList, null, null);
    }

    public static TitleCardResponse of(Title title, boolean inMyList, Integer resumeAt, Long resumeAssetId) {
        return new TitleCardResponse(
                title.getId(),
                title.getName(),
                title.getSynopsis(),
                title.getType() == null ? null : title.getType().name(),
                title.getMaturityRating() == null ? null : title.getMaturityRating().dbValue(),
                title.getPosterKey(),
                title.getBackdropKey(),
                resumeAt,
                resumeAssetId,
                inMyList);
    }
}
