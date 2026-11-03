package com.kmicro.shared.money;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores a {@link CurrencyCode} in a {@code currency char(3)} column. Applied to every entity on its own. */
@Converter(autoApply = true)
public class CurrencyCodeConverter implements AttributeConverter<CurrencyCode, String> {

    @Override
    public String convertToDatabaseColumn(CurrencyCode currency) {
        return currency == null ? null : currency.code();
    }

    @Override
    public CurrencyCode convertToEntityAttribute(String code) {
        return code == null ? null : CurrencyCode.of(code.trim());
    }
}
