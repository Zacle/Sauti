package com.sauti.business.infrastructure;

import com.sauti.business.domain.BusinessProfile;
import com.sauti.business.domain.BusinessProfileRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data implements every method declared on the domain port. */
public interface SpringDataBusinessProfileRepository
        extends Repository<BusinessProfile, UUID>, BusinessProfileRepository {
}
