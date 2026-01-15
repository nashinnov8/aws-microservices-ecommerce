package com.ecommerce.authservice.domain.repository;

import com.ecommerce.authservice.domain.entity.RefreshToken;
import com.ecommerce.authservice.domain.entity.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void deleteByUser(UserCredential user);
    List<RefreshToken> findByUser(UserCredential user);
}
