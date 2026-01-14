package com.aditya.buyogo.repo;

import com.aditya.buyogo.models.MachineEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
@Repository
public interface MachineEventRepository extends JpaRepository<MachineEvent  , Long> {

    Optional<MachineEvent> findByEventId(String eventId);

    List<MachineEvent> findByMachineIdAndEventTimeBetween(
            String machineId,
            Instant start,
            Instant end
    );

    @Query("""
        SELECT 
            m.lineId as lineId,
            SUM(m.defectCount) as totalDefects,
            COUNT(m) as eventCount
        FROM MachineEvent m
        WHERE m.factoryId = :factoryId
          AND m.eventTime >= :from
          AND m.eventTime < :to
          AND m.defectCount >= 0
        GROUP BY m.lineId
        ORDER BY SUM(m.defectCount) DESC
    """)
    List<TopDefectLineProjection> findTopDefectLines(
            String factoryId,
            Instant from,
            Instant to
    );

}
