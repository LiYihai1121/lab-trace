package com.labtrace.service;

import com.labtrace.dto.response.CheckinCodeResponse;
import com.labtrace.dto.response.CheckinRecordResponse;
import com.labtrace.dto.response.CheckinResponse;
import com.labtrace.dto.response.StatusResponse;
import com.labtrace.exception.BadRequestException;
import com.labtrace.model.CheckinCode;
import com.labtrace.model.CheckinRecord;
import com.labtrace.repository.CheckinCodeRepository;
import com.labtrace.repository.CheckinRecordRepository;
import com.labtrace.repository.UserRepository;
import com.labtrace.util.DateTimeUtil;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckinService {

  private final CheckinRecordRepository recordRepository;
  private final CheckinCodeRepository codeRepository;
  private final UserRepository userRepository;

  public CheckinService(CheckinRecordRepository recordRepository, CheckinCodeRepository codeRepository,
                        UserRepository userRepository) {
    this.recordRepository = recordRepository;
    this.codeRepository = codeRepository;
    this.userRepository = userRepository;
  }

  public StatusResponse status(Long userId) {
    CheckinRecord active = recordRepository
        .findTopByUserIdAndStatusOrderByIdDesc(userId, CheckinRecord.Status.checked_in)
        .orElse(null);

    String todayStart = DateTimeUtil.now().split(" ")[0] + " 00:00:00";
    long sessions = recordRepository.countByUserIdAndCheckinTimeAfter(userId, todayStart);
    Long minutes = recordRepository.sumDurationMinutesByUserIdAndCheckinTimeAfter(userId, todayStart);

    if (active == null) {
      return new StatusResponse(null, (int) sessions, minutes != null ? minutes.intValue() : 0);
    }

    CheckinRecordResponse activeResponse = CheckinRecordResponse.of(
        active, active.getUser().getName(), active.getUser().getUsername()
    );
    return new StatusResponse(activeResponse, (int) sessions, minutes != null ? minutes.intValue() : 0);
  }

  @Transactional
  public CheckinResponse checkin(Long userId, String code) {
    if (code == null || code.isBlank()) {
      throw new BadRequestException("请输入签到码");
    }
    String normalized = code.trim().toUpperCase();

    CheckinCode codeEntity = codeRepository.findByCodeAndExpiresAtAfter(normalized, DateTimeUtil.now())
        .orElseThrow(() -> new BadRequestException("签到码无效或已过期，请联系管理员"));

    CheckinRecord existing = recordRepository
        .findTopByUserIdAndStatusOrderByIdDesc(userId, CheckinRecord.Status.checked_in)
        .orElse(null);
    if (existing != null) {
      throw new BadRequestException("您已处于签到状态，请先签退");
    }

    CheckinRecord record = new CheckinRecord();
    record.setUser(userRepository.findById(userId).orElseThrow());
    record.setCheckinTime(DateTimeUtil.now());
    record.setStatus(CheckinRecord.Status.checked_in);
    record.setCodeId(codeEntity.getId());
    try {
      recordRepository.save(record);
    } catch (DataIntegrityViolationException ex) {
      throw new BadRequestException("您已处于签到状态，请先签退");
    }

    return new CheckinResponse("签到成功",
        CheckinRecordResponse.of(record, record.getUser().getName(), record.getUser().getUsername()));
  }

  @Transactional
  public CheckinResponse checkout(Long userId) {
    CheckinRecord active = recordRepository
        .findTopByUserIdAndStatusOrderByIdDesc(userId, CheckinRecord.Status.checked_in)
        .orElseThrow(() -> new BadRequestException("当前没有进行中的签到"));

    String now = DateTimeUtil.now();
    long checkoutMillis = DateTimeUtil.toEpochMilli(now);
    long checkinMillis = DateTimeUtil.toEpochMilli(active.getCheckinTime());
    int durationMinutes = (int) Math.max(0, Math.round((checkoutMillis - checkinMillis) / 60000.0));

    active.setCheckoutTime(now);
    active.setDurationMinutes(durationMinutes);
    active.setStatus(CheckinRecord.Status.completed);

    try {
      recordRepository.save(active);
    } catch (DataIntegrityViolationException ex) {
      throw new BadRequestException("当前没有进行中的签到");
    }

    return new CheckinResponse("签退成功，本次共 " + durationMinutes + " 分钟",
        CheckinRecordResponse.of(active, active.getUser().getName(), active.getUser().getUsername()));
  }
}
