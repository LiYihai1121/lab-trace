package com.labtrace.dto.response;

import com.labtrace.model.CheckinRecord;

public record StatusResponse(CheckinRecordResponse active, Integer todaySessions, Integer todayMinutes) {
}
