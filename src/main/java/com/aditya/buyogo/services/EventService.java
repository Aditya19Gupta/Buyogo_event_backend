package com.aditya.buyogo.services;

import com.aditya.buyogo.dto.ApiResponse;
import com.aditya.buyogo.dto.BatchResponseDTO;
import com.aditya.buyogo.dto.EventDTO;
import com.aditya.buyogo.dto.RejectionDTO;
import com.aditya.buyogo.models.MachineEvent;
import com.aditya.buyogo.repo.MachineEventRepository;
import com.aditya.buyogo.utils.PayloadHashUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class EventService {
    @Autowired
    private MachineEventRepository repo;

    AtomicInteger accepted = new AtomicInteger();
    AtomicInteger deduped = new AtomicInteger();
    AtomicInteger updated = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();

    List<MachineEvent> toSave = new ArrayList<>();
    List<RejectionDTO> rejections = new ArrayList<>();

    public BatchResponseDTO processEvents(List<EventDTO> events) {
        try {
            for (EventDTO event : events) {
                String payloadHash = PayloadHashUtil.generatePayloadHash(event);
                event.setPayloadHash(payloadHash);
                if (event.getDurationMs() < 0 || event.getDurationMs() > 3600000) {
                    rejected.incrementAndGet();
                    rejections.add(new RejectionDTO(event.getEventId(), "INVALID_DURATION"));
                    continue;
                } else if(event.getEventTime().isAfter(Instant.now().plusSeconds(15 * 60))){
                    rejected.incrementAndGet();
                    rejections.add(new RejectionDTO(event.getEventId(), "INVALID_EVENT_TIME"));
                    continue;
                }
                Optional<MachineEvent> existing = repo.findByEventId(event.getEventId());
                if (existing.isPresent()) {
                    MachineEvent existingEvent = existing.get();
                    if (existingEvent.getPayloadHash().equals(event.getPayloadHash())) {
                        deduped.incrementAndGet();
                        continue;
                    } else if (event.getReceivedTime().isAfter(existingEvent.getReceivedTime())) {
                        updated.incrementAndGet();
                        toSave.add(map(event));
                    } else {
                        // Older event, ignore
                        continue;
                    }
                } else {
                    accepted.incrementAndGet();
                    toSave.add(map(event));
                }
            }
            if (!toSave.isEmpty()) {
                repo.saveAll(toSave);
            }
            BatchResponseDTO response = new BatchResponseDTO();
            response.setAccepted(accepted.get());
            response.setRejected(rejected.get());
            response.setUpdated(updated.get());
            response.setDeduped(deduped.get());
            response.setRejections(rejections);
            return response;
        } catch (Exception e) {
            System.out.println("Error processing events: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to process events", e);
        }
    }

    private MachineEvent map(EventDTO event) {

        MachineEvent entity = new MachineEvent();
        entity.setEventId(event.getEventId());
        entity.setEventTime(event.getEventTime());
        entity.setReceivedTime(event.getReceivedTime());
        entity.setMachineId(event.getMachineId());
        entity.setDurationMs(event.getDurationMs());
        entity.setDefectCount(event.getDefectCount());
        entity.setFactoryId(event.getFactoryId());
        entity.setPayloadHash(event.getPayloadHash());
        entity.setLineId(event.getLineId());
        return entity;
    }

}
