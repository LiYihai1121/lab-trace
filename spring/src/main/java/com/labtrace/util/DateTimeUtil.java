package com.labtrace.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DateTimeUtil {

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private DateTimeUtil() {
  }

  public static String now() {
    return FORMATTER.format(LocalDateTime.now());
  }

  public static String format(LocalDateTime dateTime) {
    if (dateTime == null) {
      return "";
    }
    return FORMATTER.format(dateTime);
  }

  public static long toEpochMilli(String isoLike) {
    return LocalDateTime.parse(isoLike.replace(' ', 'T')).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
  }
}
