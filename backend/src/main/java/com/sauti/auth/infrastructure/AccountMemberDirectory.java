package com.sauti.auth.infrastructure;

import com.sauti.auth.domain.User;
import com.sauti.auth.domain.UserRepository;
import com.sauti.tenant.application.port.MemberDirectory;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Lets the workspace module show who its members are without depending on how accounts are stored. */
@Component
public class AccountMemberDirectory implements MemberDirectory {
    private final UserRepository users;

    public AccountMemberDirectory(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, MemberProfile> profiles(Collection<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> new MemberProfile(user.getId(), user.getEmail(), user.getDisplayName())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findUserIdByEmail(String email) {
        return users.findByEmail(User.normalizeEmail(email)).map(User::getId);
    }
}
