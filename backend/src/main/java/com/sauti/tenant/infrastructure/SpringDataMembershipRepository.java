package com.sauti.tenant.infrastructure;

import com.sauti.tenant.domain.Membership;
import com.sauti.tenant.domain.MembershipRepository;
import org.springframework.data.repository.Repository;

/** Spring Data implements every method declared on the domain port. */
public interface SpringDataMembershipRepository extends Repository<Membership, Membership.Key>, MembershipRepository {
}
