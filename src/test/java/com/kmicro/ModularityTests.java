package com.kmicro;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * The one standing test: checks the module boundaries. Plain JUnit, no Spring context, no Docker.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(KmicroApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
