package com.sp.parking.operation.domain.repository;

import com.sp.parking.operation.domain.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingTicketRepository extends JpaRepository<ParkingTicket, Long> {
  List<ParkingTicket> findTop25ByTenantIdOrderByCheckedInAtDesc(UUID tenantId);
  List<ParkingTicket> findByTenantIdAndStatusOrderByCheckedInAtDesc(UUID tenantId, TicketStatus status);
  Optional<ParkingTicket> findByTenantIdAndUuid(UUID tenantId, UUID uuid);
  Optional<ParkingTicket> findFirstByTenantIdAndPlateIgnoreCaseAndStatus(UUID tenantId, String plate, TicketStatus status);
  long countByTenantIdAndStatus(UUID tenantId, TicketStatus status);
}

