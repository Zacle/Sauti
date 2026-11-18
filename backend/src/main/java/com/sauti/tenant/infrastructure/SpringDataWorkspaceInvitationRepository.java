package com.sauti.tenant.infrastructure;

import com.sauti.tenant.domain.WorkspaceInvitation;
import com.sauti.tenant.domain.WorkspaceInvitationRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data implements every method declared on the domain port. */
public interface SpringDataWorkspaceInvitationRepository
        extends Repository<WorkspaceInvitation, UUID>, WorkspaceInvitationRepository {
}
