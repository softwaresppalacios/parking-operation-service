package com.sp.parking.operation.web.dto;

public final class SummaryDtos {
  private SummaryDtos() {}
  public record ParkingSummaryResponse(long activeTickets, long occupiedSpaces, long availableSpaces) {}
}

