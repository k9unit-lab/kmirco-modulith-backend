package com.kmicro.shared.id;

import java.util.Objects;
import java.util.UUID;

/** Id of one sellable item (a variant, its own SKU). Other modules point to it, never to the SKU text. */
public record VariantId(UUID value) {

    public VariantId {
        Objects.requireNonNull(value, "value");
    }

    public static VariantId of(UUID value) {
        return new VariantId(value);
    }

    public static VariantId newId() {
        return new VariantId(UuidV7.next());
    }
}
