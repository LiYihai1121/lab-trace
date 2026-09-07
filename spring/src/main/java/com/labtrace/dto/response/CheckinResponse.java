package com.labtrace.dto.response;

import com.labtrace.model.CheckinRecord;

public record CheckinResponse(String message, CheckinRecordResponse record) {
}
