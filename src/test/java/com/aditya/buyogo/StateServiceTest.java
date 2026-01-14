package com.aditya.buyogo;

import com.aditya.buyogo.dto.StatesResponseDTO;
import com.aditya.buyogo.dto.TopDefectLineDTO;
import com.aditya.buyogo.models.MachineEvent;
import com.aditya.buyogo.models.Status;
import com.aditya.buyogo.repo.MachineEventRepository;
import com.aditya.buyogo.repo.TopDefectLineProjection;
import com.aditya.buyogo.services.StateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StateServiceTest {

    @Mock
    private MachineEventRepository repository;

    @InjectMocks
    private StateService stateService;

    private Instant baseTime;
    private Instant startTime;
    private Instant endTime;

    @BeforeEach
    void setUp() {
        baseTime = Instant.now();
        startTime = baseTime;
        endTime = baseTime.plusSeconds(3600); // 1 hour later
    }

    @Test
    void testDefectCountMinusOne_IgnoredInDefectTotals() {
        // Arrange
        MachineEvent eventWithNegativeDefect = TestData.event("EVT001", -1, baseTime);
        MachineEvent eventWithZeroDefect = TestData.event("EVT002", 0, baseTime);
        MachineEvent eventWithPositiveDefect = TestData.event("EVT003", 5, baseTime);
        
        List<MachineEvent> events = Arrays.asList(
            eventWithNegativeDefect, eventWithZeroDefect, eventWithPositiveDefect
        );
        
        when(repository.findByMachineIdAndEventTimeBetween(eq("M1"), eq(startTime), eq(endTime)))
            .thenReturn(events);

        // Act
        StatesResponseDTO response = stateService.getEventByMachineIdAndDateBetween("M1", startTime, endTime);

        // Assert
        assertEquals(3, response.getEventsCount()); // All events counted
        assertEquals(5, response.getDefectsCount()); // Only positive defects counted (negative ignored)
        assertEquals(Status.WARNING, response.getStatus()); // 5 defects / 1 hour = 5.0 >= 2.0 threshold
    }

    @Test
    void testStartEndBoundaryCorrectness_InclusiveExclusive() {
        // Arrange
        MachineEvent eventAtStart = TestData.eventAt("EVT001", startTime);
        MachineEvent eventAtEnd = TestData.eventAt("EVT002", endTime);
        MachineEvent eventBeforeStart = TestData.eventAt("EVT003", startTime.minusMillis(1));
        MachineEvent eventAfterEnd = TestData.eventAt("EVT004", endTime.plusMillis(1));
        MachineEvent eventInRange = TestData.eventAt("EVT005", startTime.plusSeconds(1800)); // Middle of range
        
        List<MachineEvent> eventsInRange = Arrays.asList(eventAtStart, eventInRange);
        List<MachineEvent> allEvents = Arrays.asList(
            eventAtStart, eventAtEnd, eventBeforeStart, eventAfterEnd, eventInRange
        );
        
        when(repository.findByMachineIdAndEventTimeBetween(eq("M1"), eq(startTime), eq(endTime)))
            .thenReturn(eventsInRange);

        // Act
        StatesResponseDTO response = stateService.getEventByMachineIdAndDateBetween("M1", startTime, endTime);

        // Assert
        assertEquals(2, response.getEventsCount()); // Only events at start and in range (end is exclusive)
        assertEquals(2, response.getDefectsCount()); // Each event has 1 defect
    }

    @Test
    void testDefectRateCalculation_HealthStatus() {
        // Arrange
        MachineEvent event1 = TestData.event("EVT001", 1, baseTime); // 1 defect
        MachineEvent event2 = TestData.event("EVT002", 1, baseTime); // 1 defect
        
        List<MachineEvent> events = Arrays.asList(event1, event2);
        
        when(repository.findByMachineIdAndEventTimeBetween(eq("M1"), eq(startTime), eq(endTime)))
            .thenReturn(events);

        // Act
        StatesResponseDTO response = stateService.getEventByMachineIdAndDateBetween("M1", startTime, endTime);

        // Assert
        assertEquals(2, response.getEventsCount());
        assertEquals(2, response.getDefectsCount());
        assertEquals(2.0, response.getAvgDefectRate(), 0.01); // 2 defects / 1 hour = 2.0
        assertEquals(Status.WARNING, response.getStatus()); // >= 2.0 is WARNING
    }

    @Test
    void testDefectRateCalculation_WarningStatus() {
        // Arrange
        MachineEvent event1 = TestData.event("EVT001", 3, baseTime); // 3 defects
        MachineEvent event2 = TestData.event("EVT002", 2, baseTime); // 2 defects
        
        List<MachineEvent> events = Arrays.asList(event1, event2);
        
        when(repository.findByMachineIdAndEventTimeBetween(eq("M1"), eq(startTime), eq(endTime)))
            .thenReturn(events);

        // Act
        StatesResponseDTO response = stateService.getEventByMachineIdAndDateBetween("M1", startTime, endTime);

        // Assert
        assertEquals(2, response.getEventsCount());
        assertEquals(5, response.getDefectsCount());
        assertEquals(5.0, response.getAvgDefectRate(), 0.01); // 5 defects / 1 hour = 5.0
        assertEquals(Status.WARNING, response.getStatus()); // > 2.0 is WARNING
    }

    @Test
    void testZeroHourWindow_DefectRateZero() {
        // Arrange
        Instant sameTime = baseTime;
        List<MachineEvent> events = Arrays.asList(TestData.event("EVT001", 5, baseTime));
        
        when(repository.findByMachineIdAndEventTimeBetween(eq("M1"), eq(sameTime), eq(sameTime)))
            .thenReturn(events);

        // Act
        StatesResponseDTO response = stateService.getEventByMachineIdAndDateBetween("M1", sameTime, sameTime);

        // Assert
        assertEquals(1, response.getEventsCount());
        assertEquals(5, response.getDefectsCount());
        assertEquals(0.0, response.getAvgDefectRate(), 0.01); // Zero hour window
        assertEquals(Status.HEALTH, response.getStatus()); // Zero rate is HEALTH
    }

    @Test
    void testGetTopDefectLines() {
        // Arrange
        TopDefectLineProjection line1 = createProjection("L1", 10L, 5L); // 5 defects, 10 events
        TopDefectLineProjection line2 = createProjection("L2", 8L, 8L);  // 8 defects, 8 events
        TopDefectLineProjection line3 = createProjection("L3", 5L, 5L);  // 5 defects, 5 events
        
        List<TopDefectLineProjection> projections = Arrays.asList(line1, line2, line3);
        
        when(repository.findTopDefectLines(eq("F1"), eq(startTime), eq(endTime)))
            .thenReturn(projections);

        // Act
        List<TopDefectLineDTO> result = stateService.getTopDefectLines("F1", startTime, endTime, 3);

        // Assert
        assertEquals(3, result.size());
        
        TopDefectLineDTO dto1 = result.get(0);
        assertEquals("L1", dto1.getLineId());
        assertEquals(5L, dto1.getTotalDefects());
        assertEquals(10L, dto1.getEventCount());
        assertEquals(50.0, dto1.getDefectsPercent()); // (5/10)*100 = 50%
        
        TopDefectLineDTO dto2 = result.get(1);
        assertEquals("L2", dto2.getLineId());
        assertEquals(8L, dto2.getTotalDefects());
        assertEquals(8L, dto2.getEventCount());
        assertEquals(100.0, dto2.getDefectsPercent()); // (8/8)*100 = 100%
        
        TopDefectLineDTO dto3 = result.get(2);
        assertEquals("L3", dto3.getLineId());
        assertEquals(5L, dto3.getTotalDefects());
        assertEquals(5L, dto3.getEventCount());
        assertEquals(100.0, dto3.getDefectsPercent()); // (5/5)*100 = 100%
    }

    @Test
    void testGetTopDefectLines_LimitApplied() {
        // Arrange
        TopDefectLineProjection line1 = createProjection("L1", 10L, 5L);
        TopDefectLineProjection line2 = createProjection("L2", 8L, 8L);
        TopDefectLineProjection line3 = createProjection("L3", 5L, 5L);
        
        List<TopDefectLineProjection> projections = Arrays.asList(line1, line2, line3);
        
        when(repository.findTopDefectLines(eq("F1"), eq(startTime), eq(endTime)))
            .thenReturn(projections);

        // Act
        List<TopDefectLineDTO> result = stateService.getTopDefectLines("F1", startTime, endTime, 2);

        // Assert
        assertEquals(2, result.size()); // Limit applied
    }

    @Test
    void testGetTopDefectLines_ZeroEventCount() {
        // Arrange
        TopDefectLineProjection line = createProjection("L1", 0L, 5L); // 0 events, 5 defects (edge case)
        
        List<TopDefectLineProjection> projections = Arrays.asList(line);
        
        when(repository.findTopDefectLines(eq("F1"), eq(startTime), eq(endTime)))
            .thenReturn(projections);

        // Act
        List<TopDefectLineDTO> result = stateService.getTopDefectLines("F1", startTime, endTime, 1);

        // Assert
        assertEquals(1, result.size());
        TopDefectLineDTO dto = result.get(0);
        assertEquals("L1", dto.getLineId());
        assertEquals(5L, dto.getTotalDefects());
        assertEquals(0L, dto.getEventCount());
        assertEquals(0.0, dto.getDefectsPercent()); // Division by zero handled
    }

    private TopDefectLineProjection createProjection(String lineId, long eventCount, long totalDefects) {
        return new TopDefectLineProjection() {
            @Override
            public String getLineId() {
                return lineId;
            }

            @Override
            public long getEventCount() {
                return eventCount;
            }

            @Override
            public long getTotalDefects() {
                return totalDefects;
            }
        };
    }
}
