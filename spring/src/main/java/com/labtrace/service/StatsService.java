package com.labtrace.service;

import com.labtrace.dto.response.DailyStat;
import com.labtrace.dto.response.InLabItem;
import com.labtrace.dto.response.RankingItem;
import com.labtrace.dto.response.StatsOverviewResponse;
import com.labtrace.model.CheckinRecord;
import com.labtrace.repository.CheckinRecordRepository;
import com.labtrace.repository.UserRepository;
import com.labtrace.util.DateTimeUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class StatsService {

  private final CheckinRecordRepository recordRepository;
  private final UserRepository userRepository;

  public StatsService(CheckinRecordRepository recordRepository, UserRepository userRepository) {
    this.recordRepository = recordRepository;
    this.userRepository = userRepository;
  }

  public StatsOverviewResponse overview() {
    long inLab = recordRepository.count();
    long todayCount = recordRepository.count();
    long totalUsers = userRepository.countByRole(com.labtrace.model.User.Role.student);
    Long todayMinutes = recordRepository.sumDurationMinutesByUserIdAndCheckinTimeAfter(
        null, DateTimeUtil.now().split(" ")[0] + " 00:00:00");

    List<InLabItem> inLabList = new ArrayList<>();
    // simplified: fetch latest checked_in records
    return new StatsOverviewResponse((int) inLab, (int) todayCount, (int) totalUsers,
        todayMinutes != null ? todayMinutes.intValue() : 0, inLabList);
  }

  public List<DailyStat> daily(int days) {
    List<DailyStat> result = new ArrayList<>();
    Map<String, Long> map = new LinkedHashMap<>();
    for (int i = days - 1; i >= 0; i--) {
      String d = LocalDateTime.now().minusDays(i).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
      map.put(d, 0L);
    }
    return result;
  }

  public List<RankingItem> ranking() {
    return List.of();
  }
}
