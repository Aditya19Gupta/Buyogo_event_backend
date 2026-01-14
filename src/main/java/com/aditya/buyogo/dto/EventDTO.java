package com.aditya.buyogo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
@Data
@NoArgsConstructor
public class EventDTO {
    private String eventId;
    private Instant eventTime;
    private String machineId;
    private long durationMs;
    private int defectCount;
    private Instant receivedTime;
    private String factoryId;
    private String lineId;

    private String payloadHash;


}
