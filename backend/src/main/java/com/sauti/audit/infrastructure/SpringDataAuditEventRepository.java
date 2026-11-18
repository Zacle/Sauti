package com.sauti.audit.infrastructure;

import com.sauti.audit.domain.AuditEvent;
import com.sauti.audit.domain.AuditEventRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data implements every method declared on the domain port. */
public interface SpringDataAuditEventRepository extends Repository<AuditEvent, UUID>, AuditEventRepository {
}
