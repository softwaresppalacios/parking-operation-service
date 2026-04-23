package com.sp.parking.operation.web.controller;

import com.sp.parking.operation.application.service.ParkingSummaryService;
import com.sp.parking.operation.web.dto.SummaryDtos.ParkingSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/parking-summary")
@RequiredArgsConstructor
public class SummaryController {
  private final ParkingSummaryService summaryService;

  @GetMapping("/current")
  public ParkingSummaryResponse current(@RequestHeader("X-Tenant-Id") UUID tenantId) {
    return summaryService.current(tenantId);
  }
}

