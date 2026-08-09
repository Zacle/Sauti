package com.sauti.reliability;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReliabilityDrillRepository extends JpaRepository<ReliabilityDrill, UUID> {
    boolean existsByStatusIn(Collection<String> statuses);
    List<ReliabilityDrill> findTop20ByOrderByInitiatedAtDesc();
}
