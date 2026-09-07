package com.labtrace.repository;

import com.labtrace.model.CheckinCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckinCodeRepository extends JpaRepository<CheckinCode, Long> {
  Optional<CheckinCode> findByCodeAndExpiresAtAfter(String code, String expiresAt);
}
