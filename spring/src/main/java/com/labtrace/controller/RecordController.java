package com.labtrace.controller;

import com.labtrace.service.RecordService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/records")
public class RecordController {

  private final RecordService recordService;

  public RecordController(RecordService recordService) {
    this.recordService = recordService;
  }

  @GetMapping("/my")
  public PageResponse<RecordResponse> my(@CurrentUser UserResponse user,
                                         @RequestParam(required = false) String start,
                                         @RequestParam(required = false) String end,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int pageSize) {
    return recordService.my(user.id(), start, end, page, pageSize);
  }

  @GetMapping("/all")
  public PageResponse<RecordResponse> all(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String start,
                                          @RequestParam(required = false) String end,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize) {
    return recordService.all(keyword, start, end, page, pageSize);
  }
}
