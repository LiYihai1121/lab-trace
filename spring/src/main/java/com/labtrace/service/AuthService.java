package com.labtrace.service;

import com.labtrace.config.SecurityUtil;
import com.labtrace.dto.response.LoginResponse;
import com.labtrace.dto.response.UserResponse;
import com.labtrace.exception.BadRequestException;
import com.labtrace.exception.NotFoundException;
import com.labtrace.model.CheckinRecord;
import com.labtrace.model.PasswordResetToken;
import com.labtrace.model.User;
import com.labtrace.repository.CheckinRecordRepository;
import com.labtrace.repository.PasswordResetTokenRepository;
import com.labtrace.repository.UserRepository;
import com.labtrace.util.DateTimeUtil;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private final UserRepository userRepository;
  private final BCryptPasswordEncoder passwordEncoder;
  private final PasswordResetTokenRepository tokenRepository;
  private final CheckinRecordRepository recordRepository;

  public AuthService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder,
                     PasswordResetTokenRepository tokenRepository, CheckinRecordRepository recordRepository) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.tokenRepository = tokenRepository;
    this.recordRepository = recordRepository;
  }

  public LoginResponse login(String username, String password) {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new BadRequestException("用户名或密码错误"));

    BCrypt.checkpw(password, passwordEncoder.encode(SecurityUtil.dummyRaw()));
    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new BadRequestException("用户名或密码错误");
    }

    String token = SecurityUtil.buildToken(user.getId(), user.getUsername(), user.getName(), user.getRole().name());
    return new LoginResponse(token, UserResponse.from(user));
  }

  @Transactional
  public void changePassword(Long userId, String oldPassword, String newPassword) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException("用户不存在"));
    if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
      throw new BadRequestException("原密码错误");
    }
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);
  }

  @Transactional
  public void resetPassword(String username, String resetCode, String newPassword) {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new BadRequestException("找回码无效或已过期"));

    String tokenHash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(resetCode.trim().toUpperCase());
    PasswordResetToken token = tokenRepository
        .findTopByUserIdAndTokenHashAndUsedAtIsNullAndExpiresAtAfterOrderByIdDesc(
            user.getId(), tokenHash, DateTimeUtil.now())
        .orElseThrow(() -> new BadRequestException("找回码无效或已过期"));

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    token.setUsedAt(DateTimeUtil.now());
    userRepository.save(user);
    tokenRepository.save(token);
  }

  @Transactional
  public String issueResetCode(Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException("用户不存在"));

    String code = String.format("%012X", RANDOM.nextLong()).substring(0, 12);
    String tokenHash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(code);
    String expiresAt = DateTimeUtil.format(LocalDateTime.now().plus(15, ChronoUnit.MINUTES));

    tokenRepository.invalidateUnusedTokens(userId, DateTimeUtil.now());
    PasswordResetToken token = new PasswordResetToken();
    token.setUser(user);
    token.setTokenHash(tokenHash);
    token.setExpiresAt(expiresAt);
    token.setCreatedAt(DateTimeUtil.now());
    tokenRepository.save(token);

    return code;
  }
}
