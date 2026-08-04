package com.sauti.demo;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PilotInvitationRepository extends JpaRepository<PilotInvitation, UUID> {
    Optional<PilotInvitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from PilotInvitation invitation where invitation.tokenHash = :tokenHash")
    Optional<PilotInvitation> findLockedByTokenHash(@Param("tokenHash") String tokenHash);

    boolean existsByDemoRequestId(UUID demoRequestId);

    Optional<PilotInvitation> findByDemoRequestId(UUID demoRequestId);

    java.util.List<PilotInvitation> findAllByDemoRequestIdIn(java.util.Collection<UUID> demoRequestIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from PilotInvitation invitation where invitation.demoRequestId = :demoRequestId")
    Optional<PilotInvitation> findLockedByDemoRequestId(@Param("demoRequestId") UUID demoRequestId);
}
