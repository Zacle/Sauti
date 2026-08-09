package com.sauti.reliability;

import java.time.OffsetDateTime;
import java.util.UUID;

record ReliabilityAlertRequested(UUID incidentId, String provider, String severity,
                                 String summary, boolean recovery, OffsetDateTime occurredAt) { }
