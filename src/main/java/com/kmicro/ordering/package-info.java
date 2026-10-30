@ApplicationModule(allowedDependencies = {"shared", "settings::api", "cart::api", "catalog::api", "inventory::api", "payment::api", "payment::events"})
package com.kmicro.ordering;

import org.springframework.modulith.ApplicationModule;
