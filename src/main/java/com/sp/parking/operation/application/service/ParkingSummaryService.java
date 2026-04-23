package com.sp.parking.operation.application.service;

import com.sp.parking.operation.domain.model.TicketStatus;
import com.sp.parking.operation.domain.model.ResourceKind;
import com.sp.parking.operation.domain.repository.*;
import com.sp.parking.operation.web.dto.SummaryDtos.ParkingSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ParkingSummaryService {
  private final ParkingTicketRepository ticketRepository;
  private final ParkingResourceRepository resourceRepository;

  @Transactional(readOnly = true)
  public ParkingSummaryResponse current(UUID tenantId) {
    return new ParkingSummaryResponse(
      ticketRepository.countByTenantIdAndStatus(tenantId, TicketStatus.ACTIVE),
      resourceRepository.countByTenantIdAndKindAndOccupiedTrue(tenantId, ResourceKind.PARKING_SPACE),
      resourceRepository.countByTenantIdAndKindAndOccupiedFalse(tenantId, ResourceKind.PARKING_SPACE)
    );
  }
}
