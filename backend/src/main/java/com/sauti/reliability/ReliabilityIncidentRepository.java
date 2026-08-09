package com.sauti.reliability;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReliabilityIncidentRepository extends JpaRepository<ReliabilityIncident, UUID> {
    Optional<ReliabilityIncident> findFirstByProviderAndStatusOrderByFirstDetectedAtDesc(
            String provider, String status);
    List<ReliabilityIncident> findAllByStatus(String status);
    List<ReliabilityIncident> findTop50ByOrderByFirstDetectedAtDesc();
}
