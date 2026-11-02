package com.kmicro.shared.id;

import java.util.Objects;
import java.util.UUID;

/** Id of an order. */
public record OrderId(UUID value) {

    public OrderId {
        Objects.requireNonNull(value, "value");
    }

    public static OrderId of(UUID value) {
        return new OrderId(value);
    }

    public static OrderId newId() {
        return new OrderId(UuidV7.next());
    }
}
