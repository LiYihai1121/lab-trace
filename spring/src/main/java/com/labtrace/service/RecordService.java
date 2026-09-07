package com.labtrace.service;

import com.labtrace.dto.response.RecordResponse;
import com.labtrace.model.CheckinRecord;
import com.labtrace.repository.CheckinRecordRepository;
import com.labtrace.repository.UserRepository;
import com.labtrace.util.DateTimeUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class RecordService {

  private final CheckinRecordRepository recordRepository;
  private final UserRepository userRepository;

  public RecordService(CheckinRecordRepository recordRepository, UserRepository userRepository) {
    this.recordRepository = recordRepository;
    this.userRepository = userRepository;
  }

  public com.labtrace.dto.response.PageResponse<RecordResponse> my(Long userId, String start, String end,
                                                                   int page, int pageSize) {
    Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(pageSize, 100));
    String startBound = buildStartBound(start);
    String endBound = buildEndBound(end);

    Page<CheckinRecord> result = recordRepository.findAll(pageable);

    List<RecordResponse> content = new ArrayList<>();
    for (CheckinRecord r : result.getContent()) {
      if (r.getUser() != null && r.getUser().getId().equals(userId)) {
        if (matchesDate(r, startBound, endBound)) {
          content.add(new RecordResponse(r.getId(), r.getUser().getId(), r.getCheckinTime(), r.getCheckoutTime(),
              r.getDurationMinutes(), r.getStatus().name(), r.getCodeId(), r.getUser().getName(),
              r.getUser().getUsername()));
        }
      }
    }
    return new com.labtrace.dto.response.PageResponse<>(content, result.getTotalElements(), page, pageSize);
  }

  public com.labtrace.dto.response.PageResponse<RecordResponse> all(String keyword, String start, String end,
                                                                    int page, int pageSize) {
    Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(pageSize, 100));
    String startBound = buildStartBound(start);
    String endBound = buildEndBound(end);

    Page<CheckinRecord> result = recordRepository.findAll(pageable);

    List<RecordResponse> content = new ArrayList<>();
    for (CheckinRecord r : result.getContent()) {
      if (matchesKeyword(r, keyword) && matchesDate(r, startBound, endBound)) {
        content.add(new RecordResponse(r.getId(), r.getUser().getId(), r.getCheckinTime(), r.getCheckoutTime(),
            r.getDurationMinutes(), r.getStatus().name(), r.getCodeId(), r.getUser().getName(),
            r.getUser().getUsername()));
      }
    }
    return new com.labtrace.dto.response.PageResponse<>(content, result.getTotalElements(), page, pageSize);
  }

  private boolean matchesKeyword(CheckinRecord r, String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return true;
    }
    String kw = keyword.toLowerCase();
    String name = r.getUser().getName() != null ? r.getUser().getName().toLowerCase() : "";
    String username = r.getUser().getUsername() != null ? r.getUser().getUsername().toLowerCase() : "";
    return name.contains(kw) || username.contains(kw);
  }

  private boolean matchesDate(CheckinRecord r, String startBound, String endBound) {
    if (startBound != null && r.getCheckinTime().compareTo(startBound) < 0) {
      return false;
    }
    if (endBound != null && r.getCheckinTime().compareTo(endBound) > 0) {
      return false;
    }
    return true;
  }

  private String buildStartBound(String start) {
    if (start == null || start.isBlank()) {
      return null;
    }
    return start + " 00:00:00";
  }

  private String buildEndBound(String end) {
    if (end == null || end.isBlank()) {
      return null;
    }
    return end + " 23:59:59";
  }
}
