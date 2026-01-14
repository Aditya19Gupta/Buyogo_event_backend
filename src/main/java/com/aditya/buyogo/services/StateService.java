package com.aditya.buyogo.services;

import com.aditya.buyogo.dto.*;
import com.aditya.buyogo.models.MachineEvent;
import com.aditya.buyogo.models.Status;
import com.aditya.buyogo.repo.MachineEventRepository;
import com.aditya.buyogo.repo.TopDefectLineProjection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class StateService {
    @Autowired
    private MachineEventRepository machineEventRepository;
    public StatesResponseDTO getEventByMachineIdAndDateBetween(String machineId, Instant start, Instant end){
        List<MachineEvent> eventList = machineEventRepository.findByMachineIdAndEventTimeBetween(machineId, start, end);
        long validEventCount = eventList.size();
        long defectCount = eventList.stream().filter(e->
                e.getDefectCount() >= 0).mapToLong(MachineEvent::getDefectCount).sum();
        double windowHours =
                Duration.between(start, end).toSeconds() / 3600.0;
        double avgDefectRate = windowHours > 0 ? defectCount / windowHours : 0.0;

        Status status = avgDefectRate < 2.0 ? Status.HEALTH : Status.WARNING;
        StatesResponseDTO response = new StatesResponseDTO();
        response.setMachineId(machineId);
        response.setEnd(end);
        response.setStart(start);
        response.setEventsCount(validEventCount);
        response.setStatus(status);
        response.setDefectsCount(defectCount);
        response.setAvgDefectRate(avgDefectRate);
        return response;
    }


    public List<TopDefectLineDTO> getTopDefectLines(
            String factoryId,
            Instant from,
            Instant to,
            Integer limit) {

        return machineEventRepository
                .findTopDefectLines(factoryId, from, to)
                .stream()
                .limit(limit)
                .map(p -> {
                    TopDefectLineDTO dto = new TopDefectLineDTO();
                    dto.setLineId(p.getLineId());
                    dto.setTotalDefects(p.getTotalDefects());
                    dto.setEventCount(p.getEventCount());

                    double percent =
                            p.getEventCount() > 0
                                    ? (p.getTotalDefects() * 100.0)
                                    / p.getEventCount()
                                    : 0.0;

                    dto.setDefectsPercent(
                            Math.round(percent * 100.0) / 100.0
                    );
                    return dto;
                })
                .toList();
    }

}
