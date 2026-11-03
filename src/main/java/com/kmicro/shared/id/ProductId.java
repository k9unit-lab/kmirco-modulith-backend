package com.kmicro.shared.id;

import java.util.Objects;
import java.util.UUID;

/** Id of a catalog product. */
public record ProductId(UUID value) {

    public ProductId {
        Objects.requireNonNull(value, "value");
    }

    public static ProductId of(UUID value) {
        return new ProductId(value);
    }

    public static ProductId newId() {
        return new ProductId(UuidV7.next());
    }
}
