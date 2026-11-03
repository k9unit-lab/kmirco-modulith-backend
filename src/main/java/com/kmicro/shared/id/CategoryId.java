package com.kmicro.shared.id;

import java.util.Objects;
import java.util.UUID;

/** Id of a catalog category. */
public record CategoryId(UUID value) {

    public CategoryId {
        Objects.requireNonNull(value, "value");
    }

    public static CategoryId of(UUID value) {
        return new CategoryId(value);
    }

    public static CategoryId newId() {
        return new CategoryId(UuidV7.next());
    }
}
