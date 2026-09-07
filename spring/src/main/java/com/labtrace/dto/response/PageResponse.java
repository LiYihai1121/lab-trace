package com.labtrace.dto.response;

import java.util.List;

public record PageResponse<T>(List<T> content, long total, int page, int pageSize) {
}
