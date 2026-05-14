package com.inventory.auth.repository;

import com.inventory.auth.entity.OAuth2Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OAuth2AccountRepository extends JpaRepository<OAuth2Account, Long> {

    Optional<OAuth2Account> findByProviderAndProviderId(String provider, String providerId);

    Optional<OAuth2Account> findByProviderAndEmail(String provider, String email);
}