package com.labtrace.dto.response;

import java.util.List;

public record StatsOverviewResponse(int inLab, int todayCount, int totalUsers, int todayMinutes,
                                    List<InLabItem> inLabList) {

  public record InLabItem(Long id, String checkinTime, String name, String username) {
  }
}
