package com.labtrace.controller;

import com.labtrace.dto.response.LoginResponse;
import com.labtrace.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  public record LoginRequest(@NotBlank String username, @NotBlank String password) {
  }

  public record PasswordChangeRequest(@NotBlank String oldPassword, @NotBlank String newPassword) {
  }

  public record PasswordResetRequest(@NotBlank String username, @NotBlank String resetCode, @NotBlank String newPassword) {
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest req) {
    return authService.login(req.username(), req.password());
  }

  @GetMapping("/me")
  public UserResponse me(@CurrentUser UserResponse user) {
    return user;
  }

  @PutMapping("/password")
  public MessageResponse changePassword(@CurrentUser UserResponse user,
                                        @Valid @RequestBody PasswordChangeRequest req) {
    authService.changePassword(user.id(), req.oldPassword(), req.newPassword());
    return new MessageResponse("密码修改成功");
  }

  @PostMapping("/password/reset")
  public MessageResponse resetPassword(@Valid @RequestBody PasswordResetRequest req) {
    authService.resetPassword(req.username(), req.resetCode(), req.newPassword());
    return new MessageResponse("密码重置成功，请使用新密码登录");
  }
}
