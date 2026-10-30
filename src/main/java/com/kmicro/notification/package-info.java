@ApplicationModule(allowedDependencies = {"shared", "settings::api", "identity::events", "ordering::events", "payment::events"})
package com.kmicro.notification;

import org.springframework.modulith.ApplicationModule;
