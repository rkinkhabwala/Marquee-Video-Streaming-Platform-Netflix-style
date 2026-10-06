package com.marquee.api.catalog;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MaturityRatingConverter implements AttributeConverter<MaturityRating, String> {
    @Override
    public String convertToDatabaseColumn(MaturityRating attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public MaturityRating convertToEntityAttribute(String dbData) {
        return dbData == null ? null : MaturityRating.fromDbValue(dbData);
    }
}
