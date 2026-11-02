package com.kmicro.shared.id;

import java.util.Objects;
import java.util.UUID;

/** Id of a seller. Phase 1 has one seller, the shop itself: {@link #HOUSE}. */
public record SellerId(UUID value) {
    /** The shop itself: the seller of every product and order line in phase 1. Never change this value. */
    public static final SellerId HOUSE = new SellerId(UUID.fromString("00000000-0000-7000-8000-000000000001"));

    public SellerId {
        Objects.requireNonNull(value, "value");
    }

    public static SellerId of(UUID value) {
        return new SellerId(value);
    }

    public static SellerId newId() {
        return new SellerId(UuidV7.next());
    }
}
