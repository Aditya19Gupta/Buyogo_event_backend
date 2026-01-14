package com.aditya.buyogo;

import com.aditya.buyogo.dto.BatchResponseDTO;
import com.aditya.buyogo.dto.EventDTO;
import com.aditya.buyogo.dto.StatesResponseDTO;
import com.aditya.buyogo.models.MachineEvent;
import com.aditya.buyogo.repo.MachineEventRepository;
import com.aditya.buyogo.services.EventService;
import com.aditya.buyogo.services.StateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:testdb"
})
class EventProcessingIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MachineEventRepository repository;

    private EventService eventService;
    private StateService stateService;

    private Instant baseTime;

    @BeforeEach
    void setUp() {
        eventService = new EventService();
        eventService.repo = repository;
        
        stateService = new StateService();
        stateService.machineEventRepository = repository;
        
        baseTime = Instant.now();
    }

    @Test
    void testEndToEndProcessing_ValidAndInvalidEvents() {
        // Arrange
        EventDTO validEvent1 = TestData.eventDTO("EVT001", 2, baseTime);
        EventDTO validEvent2 = TestData.eventDTO("EVT002", 3, baseTime.minusSeconds(100));
        EventDTO invalidDurationEvent = TestData.eventDTO("EVT003", 1, baseTime);
        invalidDurationEvent.setDurationMs(-100);
        EventDTO futureEvent = TestData.eventDTO("EVT004", 1, baseTime.plusMinutes(20));

        // Act
        BatchResponseDTO response = eventService.processEvents(
            Arrays.asList(validEvent1, validEvent2, invalidDurationEvent, futureEvent)
        );

        // Assert
        assertEquals(2, response.getAccepted());
        assertEquals(2, response.getRejected());
        assertEquals(0, response.getDeduped());
        assertEquals(0, response.getUpdated());

        // Verify data was saved correctly
        List<MachineEvent> savedEvents = repository.findAll();
        assertEquals(2, savedEvents.size());
        
        // Verify state calculation
        Instant start = baseTime.minusSeconds(200);
        Instant end = baseTime.plusSeconds(200);
        StatesResponseDTO stateResponse = stateService.getEventByMachineIdAndDateBetween("M1", start, end);
        assertEquals(2, stateResponse.getEventsCount());
        assertEquals(5, stateResponse.getDefectsCount());
    }

    @Test
    void testDuplicateEventHandling() {
        // Arrange
        EventDTO originalEvent = TestData.eventDTO("EVT001", 5, baseTime);
        EventDTO duplicateEvent = TestData.eventDTO("EVT001", 5, baseTime);

        // Act
        BatchResponseDTO firstResponse = eventService.processEvents(List.of(originalEvent));
        BatchResponseDTO secondResponse = eventService.processEvents(List.of(duplicateEvent));

        // Assert
        assertEquals(1, firstResponse.getAccepted());
        assertEquals(0, firstResponse.getDeduped());
        
        assertEquals(0, secondResponse.getAccepted());
        assertEquals(1, secondResponse.getDeduped());

        // Verify only one record exists
        List<MachineEvent> savedEvents = repository.findAll();
        assertEquals(1, savedEvents.size());
        assertEquals("EVT001", savedEvents.get(0).getEventId());
    }

    @Test
    void testEventUpdateWithDifferentPayload() {
        // Arrange
        EventDTO originalEvent = TestData.eventDTO("EVT001", 3, baseTime);
        EventDTO updatedEvent = TestData.eventDTO("EVT001", 7, baseTime.plusSeconds(10));
        updatedEvent.setReceivedTime(baseTime.plusSeconds(20)); // Newer received time

        // Act
        BatchResponseDTO firstResponse = eventService.processEvents(List.of(originalEvent));
        BatchResponseDTO secondResponse = eventService.processEvents(List.of(updatedEvent));

        // Assert
        assertEquals(1, firstResponse.getAccepted());
        assertEquals(0, firstResponse.getUpdated());
        
        assertEquals(0, secondResponse.getAccepted());
        assertEquals(1, secondResponse.getUpdated());

        // Verify the event was updated
        List<MachineEvent> savedEvents = repository.findAll();
        assertEquals(1, savedEvents.size());
        MachineEvent savedEvent = savedEvents.get(0);
        assertEquals(7, savedEvent.getDefectCount()); // Should be updated
    }

    @Test
    void testThreadSafety_ConcurrentEventProcessing() throws InterruptedException, ExecutionException {
        // Arrange
        int threadCount = 20;
        int eventsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<BatchResponseDTO>> futures = new ArrayList<>();
        AtomicInteger totalAccepted = new AtomicInteger();
        AtomicInteger totalRejected = new AtomicInteger();

        // Act - Process events concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            Future<BatchResponseDTO> future = executor.submit(() -> {
                List<EventDTO> events = new ArrayList<>();
                for (int j = 0; j < eventsPerThread; j++) {
                    String eventId = "EVT_" + threadId + "_" + j;
                    EventDTO event = TestData.eventDTO(eventId, j % 5, baseTime.plusSeconds(threadId * 10 + j));
                    
                    // Make some events invalid
                    if (j % 7 == 0) {
                        event.setDurationMs(-1);
                    }
                    events.add(event);
                }
                
                BatchResponseDTO response = eventService.processEvents(events);
                totalAccepted.addAndGet(response.getAccepted());
                totalRejected.addAndGet(response.getRejected());
                return response;
            });
            futures.add(future);
        }

        // Wait for all threads to complete
        for (Future<BatchResponseDTO> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Assert
        int expectedTotalEvents = threadCount * eventsPerThread;
        int expectedRejected = threadCount * (eventsPerThread / 7); // Approximate invalid events
        
        List<MachineEvent> savedEvents = repository.findAll();
        assertEquals(totalAccepted.get(), savedEvents.size());
        assertTrue(totalRejected.get() > 0);
        assertTrue(totalAccepted.get() + totalRejected.get() <= expectedTotalEvents);

        // Verify no duplicate event IDs exist
        long uniqueEventIds = savedEvents.stream()
            .map(MachineEvent::getEventId)
            .distinct()
            .count();
        assertEquals(savedEvents.size(), uniqueEventIds);
    }

    @Test
    void testBoundaryConditions_TimeRangeQueries() {
        // Arrange
        Instant queryStart = baseTime;
        Instant queryEnd = baseTime.plusSeconds(3600);
        
        // Create events at boundary conditions
        EventDTO eventAtStart = TestData.eventDTO("EVT001", 1, queryStart);
        EventDTO eventJustAfterStart = TestData.eventDTO("EVT002", 1, queryStart.plusMillis(1));
        EventDTO eventJustBeforeEnd = TestData.eventDTO("EVT003", 1, queryEnd.minusMillis(1));
        EventDTO eventAtEnd = TestData.eventDTO("EVT004", 1, queryEnd);
        EventDTO eventAfterEnd = TestData.eventDTO("EVT005", 1, queryEnd.plusMillis(1));
        
        List<EventDTO> events = Arrays.asList(
            eventAtStart, eventJustAfterStart, eventJustBeforeEnd, eventAtEnd, eventAfterEnd
        );

        // Act
        BatchResponseDTO response = eventService.processEvents(events);
        StatesResponseDTO stateResponse = stateService.getEventByMachineIdAndDateBetween("M1", queryStart, queryEnd);

        // Assert
        assertEquals(5, response.getAccepted()); // All events accepted by EventService
        
        // StateService should find events within [start, end) range
        assertEquals(4, stateResponse.getEventsCount()); // start, just after start, just before end, but NOT at end
        assertEquals(4, stateResponse.getDefectsCount());
    }

    @Test
    void testDefectCountFiltering_NegativeValuesIgnored() {
        // Arrange
        EventDTO eventWithNegativeDefect = TestData.eventDTO("EVT001", -5, baseTime);
        EventDTO eventWithZeroDefect = TestData.eventDTO("EVT002", 0, baseTime);
        EventDTO eventWithPositiveDefect = TestData.eventDTO("EVT003", 10, baseTime);

        // Act
        BatchResponseDTO response = eventService.processEvents(
            Arrays.asList(eventWithNegativeDefect, eventWithZeroDefect, eventWithPositiveDefect)
        );

        // Assert
        assertEquals(3, response.getAccepted()); // All accepted by EventService

        // Verify StateService filters negative defects
        Instant start = baseTime.minusSeconds(100);
        Instant end = baseTime.plusSeconds(100);
        StatesResponseDTO stateResponse = stateService.getEventByMachineIdAndDateBetween("M1", start, end);
        
        assertEquals(3, stateResponse.getEventsCount()); // All events counted
        assertEquals(10, stateResponse.getDefectsCount()); // Only positive defect counted
    }

    @Test
    void testHighVolumeEventProcessing() {
        // Arrange
        int eventCount = 1000;
        List<EventDTO> events = new ArrayList<>();
        
        for (int i = 0; i < eventCount; i++) {
            EventDTO event = TestData.eventDTO("EVT_" + String.format("%04d", i), i % 10, baseTime.plusSeconds(i));
            events.add(event);
        }

        // Act
        long startTime = System.currentTimeMillis();
        BatchResponseDTO response = eventService.processEvents(events);
        long processingTime = System.currentTimeMillis() - startTime;

        // Assert
        assertEquals(eventCount, response.getAccepted());
        assertEquals(0, response.getRejected());
        
        List<MachineEvent> savedEvents = repository.findAll();
        assertEquals(eventCount, savedEvents.size());
        
        // Performance assertion - should process 1000 events in reasonable time
        assertTrue(processingTime < 10000, "Processing 1000 events should take less than 10 seconds");
        
        // Verify defect calculation accuracy
        Instant start = baseTime.minusSeconds(100);
        Instant end = baseTime.plusSeconds(eventCount + 100);
        StatesResponseDTO stateResponse = stateService.getEventByMachineIdAndDateBetween("M1", start, end);
        
        assertEquals(eventCount, stateResponse.getEventsCount());
        
        // Calculate expected defect total (sum of 0-9 repeated)
        int expectedDefectTotal = (eventCount / 10) * (0 + 1 + 2 + 3 + 4 + 5 + 6 + 7 + 8 + 9);
        if (eventCount % 10 != 0) {
            // Add remainder
            for (int i = 0; i < eventCount % 10; i++) {
                expectedDefectTotal += i;
            }
        }
        assertEquals(expectedDefectTotal, stateResponse.getDefectsCount());
    }
}
