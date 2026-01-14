package com.aditya.buyogo;

import com.aditya.buyogo.dto.EventDTO;
import com.aditya.buyogo.models.MachineEvent;

import java.time.Instant;

class TestData {

    static MachineEvent event(String id, int defect) {
        return event(id, defect, Instant.now());
    }

    static MachineEvent event(String id, int defect, Instant receivedTime) {
        MachineEvent e = new MachineEvent();
        e.setEventId(id);
        e.setDefectCount(defect);
        e.setReceivedTime(receivedTime);
        e.setEventTime(Instant.now().minusSeconds(10));
        e.setMachineId("M1");
        return e;
    }

    static MachineEvent eventAt(String id, Instant eventTime) {
        MachineEvent e = event(id, 1);
        e.setEventTime(eventTime);
        return e;
    }

    static EventDTO eventDTO(String id, int defect, Instant eventTime) {
        EventDTO dto = new EventDTO();
        dto.setEventId(id);
        dto.setDefectCount(defect);
        dto.setEventTime(eventTime);
        dto.setReceivedTime(Instant.now());
        dto.setMachineId("M1");
        dto.setFactoryId("F1");
        dto.setLineId("L1");
        dto.setDurationMs(1000L);
        return dto;
    }
}
