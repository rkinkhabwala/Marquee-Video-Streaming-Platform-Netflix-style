package com.marquee.api.catalog;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Set;

public enum MaturityRating {
    G,
    PG,
    TV_Y,
    TV_Y7,
    TV_G,
    TV_PG,
    PG_13,
    R,
    NC_17,
    TV_14,
    TV_MA;

    public static final Set<MaturityRating> KIDS_SAFE = Set.of(G, PG, TV_Y, TV_Y7, TV_G, TV_PG);

    public boolean isKidsSafe() {
        return KIDS_SAFE.contains(this);
    }

    @JsonCreator
    public static MaturityRating fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        for (MaturityRating rating : values()) {
            if (rating.name().equalsIgnoreCase(normalized) || rating.dbValue().equalsIgnoreCase(normalized)) {
                return rating;
            }
        }
        throw new IllegalArgumentException("Unsupported maturity rating: " + value);
    }

    public String dbValue() {
        return switch (this) {
            case G -> "G";
            case PG -> "PG";
            case TV_Y -> "TV-Y";
            case TV_Y7 -> "TV-Y7";
            case TV_G -> "TV-G";
            case TV_PG -> "TV-PG";
            case PG_13 -> "PG-13";
            case R -> "R";
            case NC_17 -> "NC-17";
            case TV_14 -> "TV-14";
            case TV_MA -> "TV-MA";
        };
    }

    public static MaturityRating fromDbValue(String value) {
        if (value == null) {
            return null;
        }
        for (MaturityRating rating : values()) {
            if (rating.dbValue().equalsIgnoreCase(value)) {
                return rating;
            }
        }
        return valueOf(value.replace('-', '_'));
    }
}
