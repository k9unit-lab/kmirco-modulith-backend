/**
 * Shared kernel: Money, typed IDs, the error format and time helpers. Kept tiny, no business logic.
 * Open, so every module may use it.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.kmicro.shared;

import org.springframework.modulith.ApplicationModule;
