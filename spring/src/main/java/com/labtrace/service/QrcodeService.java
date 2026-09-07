package com.labtrace.service;

import com.labtrace.dto.response.CheckinCodeResponse;
import com.labtrace.exception.BadRequestException;
import com.labtrace.model.CheckinCode;
import com.labtrace.model.CheckinRecord;
import com.labtrace.repository.CheckinCodeRepository;
import com.labtrace.repository.CheckinRecordRepository;
import com.labtrace.util.DateTimeUtil;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QrcodeService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int CODE_TTL_SECONDS = 60;

  private final CheckinCodeRepository codeRepository;

  public QrcodeService(CheckinCodeRepository codeRepository) {
    this.codeRepository = codeRepository;
  }

  @Transactional
  public CheckinCodeResponse generate() {
    codeRepository.findAll().stream()
        .filter(c -> LocalDateTime.parse(c.getExpiresAt().replace(' ', 'T')).isBefore(LocalDateTime.now()))
        .forEach(c -> codeRepository.delete(c));

    String code;
    boolean inserted = false;
    for (int i = 0; i < 5 && !inserted; i++) {
      code = randomCode();
      CheckinCode entity = new CheckinCode();
      entity.setCode(code);
      entity.setExpiresAt(DateTimeUtil.format(LocalDateTime.now().plusSeconds(CODE_TTL_SECONDS)));
      entity.setCreatedAt(DateTimeUtil.now());
      try {
        codeRepository.save(entity);
        inserted = true;
      } catch (DataIntegrityViolationException ex) {
        // retry on unique constraint conflict
      }
    }
    if (!inserted) {
      throw new BadRequestException("签到码生成繁忙，请稍后重试");
    }

    CheckinCode saved = codeRepository.findAll().stream()
        .max((a, b) -> a.getId().compareTo(b.getId()))
        .orElseThrow();

    return new CheckinCodeResponse(saved.getCode(),
        LocalDateTime.parse(saved.getExpiresAt().replace(' ', 'T')).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
        CODE_TTL_SECONDS);
  }

  private String randomCode() {
    StringBuilder sb = new StringBuilder(6);
    for (int i = 0; i < 6; i++) {
      sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
    }
    return sb.toString();
  }
}
