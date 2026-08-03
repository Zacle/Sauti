package com.sauti.demo;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DemoRequestRepository extends JpaRepository<DemoRequest, UUID> {
    Optional<DemoRequest> findFirstByEmailIgnoreCaseAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            String email, OffsetDateTime since);
}
