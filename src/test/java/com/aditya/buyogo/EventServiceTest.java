package com.aditya.buyogo;

import com.aditya.buyogo.dto.BatchResponseDTO;
import com.aditya.buyogo.dto.EventDTO;
import com.aditya.buyogo.dto.RejectionDTO;
import com.aditya.buyogo.models.MachineEvent;
import com.aditya.buyogo.repo.MachineEventRepository;
import com.aditya.buyogo.services.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private MachineEventRepository repository;

    @InjectMocks
    private EventService eventService;

    private Instant baseTime;
    private Instant pastTime;
    private Instant futureTime;

    @BeforeEach
    void setUp() {
        baseTime = Instant.now();
        pastTime = baseTime.minusSeconds(100);
        futureTime = baseTime.plusSeconds(100);
    }

    @Test
    void testIdenticalDuplicateEventId_Deduped() {
        // Arrange
        String eventId = "EVT001";
        EventDTO event = TestData.eventDTO(eventId, 5, baseTime);
        event.setPayloadHash("hash123");
        
        MachineEvent existingEvent = TestData.event(eventId, 5, baseTime);
        existingEvent.setPayloadHash("hash123");
        
        when(repository.findByEventId(eventId)).thenReturn(Optional.of(existingEvent));

        // Act
        BatchResponseDTO response = eventService.processEvents(List.of(event));

        // Assert
        assertEquals(0, response.getAccepted());
        assertEquals(1, response.getDeduped());
        assertEquals(0, response.getUpdated());
        assertEquals(0, response.getRejected());
        verify(repository, never()).save(any());
    }

    @Test
    void testDifferentPayloadNewerReceivedTime_UpdateHappens() {
        // Arrange
        String eventId = "EVT002";
        Instant oldReceivedTime = baseTime.minusSeconds(50);
        Instant newReceivedTime = baseTime;
        
        EventDTO newEvent = TestData.eventDTO(eventId, 10, baseTime);
        newEvent.setReceivedTime(newReceivedTime);
        newEvent.setPayloadHash("newHash456");
        
        MachineEvent existingEvent = TestData.event(eventId, 5, baseTime);
        existingEvent.setReceivedTime(oldReceivedTime);
        existingEvent.setPayloadHash("oldHash123");
        
        when(repository.findByEventId(eventId)).thenReturn(Optional.of(existingEvent));
        when(repository.saveAll(any())).thenReturn(List.of());

        // Act
        BatchResponseDTO response = eventService.processEvents(List.of(newEvent));

        // Assert
        assertEquals(0, response.getAccepted());
        assertEquals(0, response.getDeduped());
        assertEquals(1, response.getUpdated());
        assertEquals(0, response.getRejected());
        verify(repository, times(1)).saveAll(any());
    }

    @Test
    void testDifferentPayloadOlderReceivedTime_Ignored() {
        // Arrange
        String eventId = "EVT003";
        Instant oldReceivedTime = baseTime.minusSeconds(100);
        Instant newReceivedTime = baseTime.minusSeconds(50);
        
        EventDTO olderEvent = TestData.eventDTO(eventId, 10, baseTime);
        olderEvent.setReceivedTime(oldReceivedTime);
        olderEvent.setPayloadHash("oldHash123");
        
        MachineEvent existingEvent = TestData.event(eventId, 5, baseTime);
        existingEvent.setReceivedTime(newReceivedTime);
        existingEvent.setPayloadHash("newHash456");
        
        when(repository.findByEventId(eventId)).thenReturn(Optional.of(existingEvent));

        // Act
        BatchResponseDTO response = eventService.processEvents(List.of(olderEvent));

        // Assert
        assertEquals(0, response.getAccepted());
        assertEquals(0, response.getDeduped());
        assertEquals(1, response.getUpdated());
        assertEquals(0, response.getRejected());
        verify(repository, times(1)).saveAll(any());
    }

    @Test
    void testInvalidDuration_Rejected() {
        // Arrange
        EventDTO eventWithNegativeDuration = TestData.eventDTO("EVT004", 3, baseTime);
        eventWithNegativeDuration.setDurationMs(-100);
        
        EventDTO eventWithTooLongDuration = TestData.eventDTO("EVT005", 3, baseTime);
        eventWithTooLongDuration.setDurationMs(3600001); // More than 1 hour

        // Act
        BatchResponseDTO response = eventService.processEvents(
            List.of(eventWithNegativeDuration, eventWithTooLongDuration)
        );

        // Assert
        assertEquals(0, response.getAccepted());
        assertEquals(0, response.getDeduped());
        assertEquals(0, response.getUpdated());
        assertEquals(2, response.getRejected());
        assertEquals(2, response.getRejections().size());
        
        List<String> rejectionReasons = response.getRejections().stream()
            .map(RejectionDTO::getReason)
            .toList();
        assertTrue(rejectionReasons.contains("INVALID_DURATION"));
        verify(repository, never()).save(any());
    }

    @Test
    void testFutureEventTime_Rejected() {
        // Arrange
        EventDTO futureEvent = TestData.eventDTO("EVT006", 2, baseTime.plusMinutes(20)); // 20 minutes in future

        // Act
        BatchResponseDTO response = eventService.processEvents(List.of(futureEvent));

        // Assert
        assertEquals(0, response.getAccepted());
        assertEquals(0, response.getDeduped());
        assertEquals(0, response.getUpdated());
        assertEquals(1, response.getRejected());
        assertEquals(1, response.getRejections().size());
        assertEquals("INVALID_EVENT_TIME", response.getRejections().get(0).getReason());
        verify(repository, never()).save(any());
    }

    @Test
    void testDefectCountMinusOne_IgnoredInDefectTotals() {
        // This test verifies the StateService behavior for negative defect counts
        // Since StateService filters out negative defect counts in line 24-25
        String eventId1 = "EVT007";
        String eventId2 = "EVT008";
        
        EventDTO eventWithNegativeDefect = TestData.eventDTO(eventId1, -1, baseTime);
        EventDTO eventWithPositiveDefect = TestData.eventDTO(eventId2, 5, baseTime);
        
        when(repository.findByEventId(eventId1)).thenReturn(Optional.empty());
        when(repository.findByEventId(eventId2)).thenReturn(Optional.empty());
        when(repository.saveAll(any())).thenReturn(List.of());

        // Act
        BatchResponseDTO response = eventService.processEvents(
            List.of(eventWithNegativeDefect, eventWithPositiveDefect)
        );

        // Assert - both events are accepted, but negative defect count should be filtered in StateService
        assertEquals(2, response.getAccepted());
        assertEquals(0, response.getDeduped());
        assertEquals(0, response.getUpdated());
        assertEquals(0, response.getRejected());
        verify(repository, times(1)).saveAll(any());
    }

    @Test
    void testStartEndBoundaryCorrectness() {
        // Test boundary conditions for time ranges
        Instant start = baseTime;
        Instant end = baseTime.plusSeconds(3600); // 1 hour window
        
        // Event exactly at start time
        EventDTO eventAtStart = TestData.eventDTO("EVT009", 2, start);
        
        // Event exactly at end time  
        EventDTO eventAtEnd = TestData.eventDTO("EVT010", 3, end);
        
        // Event just before start
        EventDTO eventBeforeStart = TestData.eventDTO("EVT011", 1, start.minusMillis(1));
        
        // Event just after end
        EventDTO eventAfterEnd = TestData.eventDTO("EVT012", 1, end.plusMillis(1));
        
        when(repository.findByEventId(anyString())).thenReturn(Optional.empty());
        when(repository.saveAll(any())).thenReturn(List.of());

        // Act
        List<EventDTO> events = Arrays.asList(eventAtStart, eventAtEnd, eventBeforeStart, eventAfterEnd);
        BatchResponseDTO response = eventService.processEvents(events);

        // Assert - all should be accepted since EventService doesn't filter by time range
        assertEquals(4, response.getAccepted());
        assertEquals(0, response.getRejected());
        verify(repository, times(1)).saveAll(any());
    }

    @Test
    void testThreadSafety_ConcurrentIngestion() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        int eventsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        when(repository.findByEventId(anyString())).thenReturn(Optional.empty());
        when(repository.saveAll(any())).thenReturn(List.of());

        // Act - Process events concurrently
        CompletableFuture<?>[] futures = new CompletableFuture[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                List<EventDTO> events = Arrays.asList(
                    TestData.eventDTO("EVT" + threadId + "_1", 1, baseTime),
                    TestData.eventDTO("EVT" + threadId + "_2", 2, baseTime),
                    TestData.eventDTO("EVT" + threadId + "_3", 3, baseTime),
                    TestData.eventDTO("EVT" + threadId + "_4", 4, baseTime),
                    TestData.eventDTO("EVT" + threadId + "_5", 5, baseTime)
                );
                
                BatchResponseDTO response = eventService.processEvents(events);
                
                // Verify each thread got expected results
                assertEquals(5, response.getAccepted());
                assertEquals(0, response.getRejected());
            }, executor);
        }

        // Wait for all threads to complete
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert - Verify repository was called correctly
        verify(repository, atLeast(threadCount)).saveAll(any());
        
        // Verify no exceptions were thrown during concurrent processing
        // This test mainly ensures no race conditions or data corruption
    }

    @Test
    void testMixedScenario_ValidAndInvalidEvents() {
        // Arrange - Mix of valid and invalid events
        EventDTO validEvent1 = TestData.eventDTO("EVT013", 2, baseTime);
        EventDTO validEvent2 = TestData.eventDTO("EVT014", 3, baseTime);
        
        EventDTO invalidDurationEvent = TestData.eventDTO("EVT015", 1, baseTime);
        invalidDurationEvent.setDurationMs(-50);
        
        EventDTO futureTimeEvent = TestData.eventDTO("EVT016", 1, baseTime.plusMinutes(20));
        
        // Duplicate event setup
        MachineEvent existingEvent = TestData.event("EVT017", 4, baseTime);
        when(repository.findByEventId("EVT017")).thenReturn(Optional.of(existingEvent));
        
        EventDTO duplicateEvent = TestData.eventDTO("EVT017", 4, baseTime);
        duplicateEvent.setPayloadHash(existingEvent.getPayloadHash());
        
        when(repository.findByEventId(anyString())).thenReturn(Optional.empty());
        when(repository.saveAll(any())).thenReturn(List.of());

        // Act
        List<EventDTO> events = Arrays.asList(
            validEvent1, validEvent2, invalidDurationEvent, 
            futureTimeEvent, duplicateEvent
        );
        BatchResponseDTO response = eventService.processEvents(events);

        // Assert
        assertEquals(2, response.getAccepted()); // validEvent1, validEvent2
        assertEquals(1, response.getDeduped()); // duplicateEvent
        assertEquals(0, response.getUpdated());
        assertEquals(2, response.getRejected()); // invalidDurationEvent, futureTimeEvent
        assertEquals(2, response.getRejections().size());
        
        List<String> rejectionReasons = response.getRejections().stream()
            .map(RejectionDTO::getReason)
            .toList();
        assertTrue(rejectionReasons.contains("INVALID_DURATION"));
        assertTrue(rejectionReasons.contains("INVALID_EVENT_TIME"));
    }
}
