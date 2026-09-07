package com.labtrace.controller;

import com.labtrace.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  public record CreateUserRequest(String username, String password, String name, String role) {
  }

  public record UpdateUserRequest(String name, String role, String password) {
  }

  @GetMapping
  public PageResponse<UserResponse> list(@RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int pageSize) {
    return userService.list(keyword, page, pageSize);
  }

  @PostMapping
  public IdResponse create(@Valid @RequestBody CreateUserRequest req) {
    return userService.create(req);
  }

  @PutMapping("/{id}")
  public MessageResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req) {
    userService.update(id, req);
    return new MessageResponse("保存成功");
  }

  @DeleteMapping("/{id}")
  public MessageResponse delete(@PathVariable Long id, @CurrentUser UserResponse current) {
    userService.delete(id, current.id());
    return new MessageResponse("删除成功");
  }

  @PostMapping("/{id}/password-reset-token")
  public ResetTokenResponse issueResetCode(@PathVariable Long id) {
    return userService.issueResetCode(id);
  }
}
