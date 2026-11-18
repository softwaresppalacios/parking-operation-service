package com.sp.parking.operation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ParkingOperationApplication {
  public static void main(String[] args) {
    SpringApplication.run(ParkingOperationApplication.class, args);
  }
}

