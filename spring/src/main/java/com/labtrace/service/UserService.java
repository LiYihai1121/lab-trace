package com.labtrace.service;

import com.labtrace.dto.response.IdResponse;
import com.labtrace.dto.response.MessageResponse;
import com.labtrace.dto.response.PageResponse;
import com.labtrace.dto.response.ResetTokenResponse;
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
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private final UserRepository userRepository;
  private final CheckinRecordRepository recordRepository;
  private final PasswordResetTokenRepository tokenRepository;

  public UserService(UserRepository userRepository, CheckinRecordRepository recordRepository,
                     PasswordResetTokenRepository tokenRepository) {
    this.userRepository = userRepository;
    this.recordRepository = recordRepository;
    this.tokenRepository = tokenRepository;
  }

  public PageResponse<UserResponse> list(String keyword, int page, int pageSize) {
    Pageable pageable = PageRequest.of(Math.max(0, page - 1), pageSize);
    Page<User> result = userRepository.findAll(pageable);
    List<UserResponse> content = result.getContent().stream()
        .map(UserResponse::from)
        .toList();
    return new PageResponse<>(content, result.getTotalElements(), page, pageSize);
  }

  @Transactional
  public IdResponse create(com.labtrace.controller.UserController.CreateUserRequest req) {
    if (!java.util.List.of("student", "admin").contains(req.role())) {
      throw new BadRequestException("角色无效");
    }
    User user = new User();
    user.setUsername(req.username());
    user.setName(req.name());
    user.setRole(User.Role.valueOf(req.role()));
    user.setCreatedAt(DateTimeUtil.now());
    userRepository.save(user);
    return new IdResponse(user.getId());
  }

  @Transactional
  public void update(Long id, com.labtrace.controller.UserController.UpdateUserRequest req) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("用户不存在"));
    if (!java.util.List.of("student", "admin").contains(req.role())) {
      throw new BadRequestException("角色无效");
    }
    user.setName(req.name());
    user.setRole(User.Role.valueOf(req.role()));
    userRepository.save(user);
  }

  @Transactional
  public void delete(Long id, Long currentUserId) {
    if (id.equals(currentUserId)) {
      throw new BadRequestException("不能删除当前登录账号");
    }
    User user = userRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("用户不存在"));
    tokenRepository.invalidateUnusedTokens(id, DateTimeUtil.now());
    recordRepository.deleteByUserId(id);
    userRepository.delete(user);
  }

  @Transactional
  public ResetTokenResponse issueResetCode(Long id) {
    return new ResetTokenResponse("CODE-REPLACED", DateTimeUtil.now());
  }
}
