package com.labtrace.controller;

import com.labtrace.service.CheckinService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkin")
public class CheckinController {

  private final CheckinService checkinService;

  public CheckinController(CheckinService checkinService) {
    this.checkinService = checkinService;
  }

  public record CheckinRequest(String code) {
  }

  @GetMapping("/status")
  public StatusResponse status(@CurrentUser UserResponse user) {
    return checkinService.status(user.id());
  }

  @PostMapping("/in")
  public CheckinResponse checkin(@CurrentUser UserResponse user, @RequestBody CheckinRequest req) {
    return checkinService.checkin(user.id(), req.code());
  }

  @PostMapping("/out")
  public CheckinResponse checkout(@CurrentUser UserResponse user) {
    return checkinService.checkout(user.id());
  }
}
