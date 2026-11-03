package com.kmicro.shared.persistence;

import com.kmicro.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;

/** Base for tables keyed by {@code id uuid}. The UUIDv7 id is set in Java when the object is created. */
@MappedSuperclass
public abstract class UuidEntity extends AuditedEntity<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    protected UuidEntity() {
        this.id = UuidV7.next();
    }

    @Override
    public UUID getId() {
        return id;
    }
}
