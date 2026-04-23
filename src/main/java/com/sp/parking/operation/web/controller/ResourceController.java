package com.sp.parking.operation.web.controller;

import com.sp.parking.operation.application.service.ParkingResourceService;
import com.sp.parking.operation.web.dto.ResourceDtos.ResourceBoardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceController {
  private final ParkingResourceService resourceService;

  @GetMapping("/board")
  public ResourceBoardResponse board(@RequestHeader("X-Tenant-Id") UUID tenantId) {
    return resourceService.board(tenantId);
  }
}

