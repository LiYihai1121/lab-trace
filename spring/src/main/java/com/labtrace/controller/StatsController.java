package com.labtrace.controller;

import com.labtrace.service.StatsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

  private final StatsService statsService;

  public StatsController(StatsService statsService) {
    this.statsService = statsService;
  }

  @GetMapping("/overview")
  public StatsOverviewResponse overview() {
    return statsService.overview();
  }

  @GetMapping("/daily")
  public java.util.List<DailyStat> daily(@RequestParam(defaultValue = "30") int days) {
    return statsService.daily(days);
  }

  @GetMapping("/ranking")
  public java.util.List<RankingItem> ranking() {
    return statsService.ranking();
  }
}
