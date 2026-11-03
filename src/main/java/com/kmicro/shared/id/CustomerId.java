package com.kmicro.shared.id;

import java.util.Objects;
import java.util.UUID;

/** Id of a customer. Same value as the Keycloak user id ({@code sub}). */
public record CustomerId(UUID value) {

    public CustomerId {
        Objects.requireNonNull(value, "value");
    }

    public static CustomerId of(UUID value) {
        return new CustomerId(value);
    }

    public static CustomerId newId() {
        return new CustomerId(UuidV7.next());
    }
}
