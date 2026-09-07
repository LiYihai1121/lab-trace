package com.labtrace.dto.response;

import com.labtrace.model.CheckinRecord;

public record CheckinRecordResponse(Long id, Long userId, String checkinTime, String checkoutTime,
                                    Integer durationMinutes, String status, Long codeId, String name,
                                    String username) {

  public static CheckinRecordResponse of(CheckinRecord record, String name, String username) {
    return new CheckinRecordResponse(
        record.getId(),
        record.getUser().getId(),
        record.getCheckinTime(),
        record.getCheckoutTime(),
        record.getDurationMinutes(),
        record.getStatus().name(),
        record.getCodeId(),
        name,
        username
    );
  }
}
