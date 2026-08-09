package com.sauti.reliability;

import java.time.OffsetDateTime;
import java.util.List;

public interface QueueHealthContributor {
    List<QueueState> snapshot();

    record QueueState(String key, String label, long pending, long retrying, long exhausted,
                      OffsetDateTime oldestQueuedAt) { }
}
