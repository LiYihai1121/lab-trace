package com.labtrace.repository;

import com.labtrace.model.PasswordResetToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

  Optional<PasswordResetToken> findTopByUserIdAndTokenHashAndUsedAtIsNullAndExpiresAtAfterOrderByIdDesc(
      Long userId, String tokenHash, String expiresAt);

  @Modifying
  @Query("UPDATE PasswordResetToken t SET t.usedAt = :usedAt WHERE t.user.id = :userId AND t.usedAt IS NULL")
  int invalidateUnusedTokens(@Param("userId") Long userId, @Param("usedAt") String usedAt);
}
