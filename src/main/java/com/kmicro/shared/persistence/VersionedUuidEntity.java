package com.kmicro.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/** A {@link UuidEntity} with a {@code version} column. A stale save fails with an optimistic locking error. */
@MappedSuperclass
public abstract class VersionedUuidEntity extends UuidEntity {

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Long getVersion() {
        return version;
    }
}
