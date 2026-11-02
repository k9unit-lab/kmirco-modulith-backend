package com.kmicro.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * An {@link AuditedEntity} with a {@code version} column, for natural key tables such as {@code stock_levels}
 * (keyed by {@code variant_id}) and {@code store_settings} ({@code id = 1}). A stale save fails with an optimistic
 * locking error.
 */
@MappedSuperclass
public abstract class VersionedEntity<ID> extends AuditedEntity<ID> {

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Long getVersion() {
        return version;
    }
}
