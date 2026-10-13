package com.subin.submanager.auth.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    Optional<UserIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
