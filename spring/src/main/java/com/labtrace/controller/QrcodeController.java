package com.labtrace.controller;

import com.labtrace.service.QrcodeService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/qrcode")
public class QrcodeController {

  private final QrcodeService qrcodeService;

  public QrcodeController(QrcodeService qrcodeService) {
    this.qrcodeService = qrcodeService;
  }

  @PostMapping("/generate")
  public CheckinCodeResponse generate() {
    return qrcodeService.generate();
  }
}
