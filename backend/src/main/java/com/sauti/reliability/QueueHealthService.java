package com.sauti.reliability;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueueHealthService {
    private final List<QueueHealthContributor> contributors;

    public QueueHealthService(List<QueueHealthContributor> contributors) {
        this.contributors = contributors;
    }

    @Transactional(readOnly = true)
    public List<QueueHealthContributor.QueueState> snapshot() {
        return contributors.stream().flatMap(contributor -> contributor.snapshot().stream())
                .sorted(Comparator.comparing(QueueHealthContributor.QueueState::label)).toList();
    }
}
