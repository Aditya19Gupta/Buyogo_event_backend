package com.aditya.buyogo.dto;

import com.aditya.buyogo.models.Status;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class StatesResponseDTO {
    private String machineId;
    private Instant start;
    private Instant end;
    private Long eventsCount;
    private Long defectsCount;
    private Double avgDefectRate;
    private Status status;
    private String factoryId;
}
