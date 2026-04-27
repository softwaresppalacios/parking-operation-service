package com.sp.parking.operation.web.controller;

import com.sp.parking.operation.application.service.MonthlySubscriptionService;
import com.sp.parking.operation.web.dto.MonthlySubscriptionDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/monthly-subscriptions")
@RequiredArgsConstructor
public class MonthlySubscriptionController {
  private final MonthlySubscriptionService service;

  @GetMapping
  public List<MonthlySubscriptionResponse> all(@RequestHeader("X-Tenant-Id") UUID tenantId) {
    return service.findAll(tenantId);
  }

  @PostMapping
  public MonthlySubscriptionResponse create(@RequestHeader("X-Tenant-Id") UUID tenantId, @Valid @RequestBody CreateMonthlySubscriptionRequest request) {
    return service.create(tenantId, request);
  }
}
