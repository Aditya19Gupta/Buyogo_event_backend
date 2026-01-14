# Buyogo Event Processing System

A high-performance Spring Boot application for processing machine events with deduplication, validation, and real-time analytics.

## Architecture

### System Overview
The application follows a **layered architecture** with clear separation of concerns:

```
┌─────────────────┐
│   Controllers   │ ← REST API endpoints
├─────────────────┤
│    Services     │ ← Business logic & processing
├─────────────────┤
│  Repositories   │ ← Data access layer
├─────────────────┤
│    Database     │ ← PostgreSQL (production) / H2 (test)
└─────────────────┘
```

### Key Components

1. **EventIngestionController**: REST endpoint for batch event ingestion
2. **StatesController**: Query endpoints for machine state analytics
3. **EventService**: Core event processing logic with deduplication
4. **StateService**: Analytics and state calculations
5. **MachineEventRepository**: Data access with custom queries
6. **PayloadHashUtil**: Payload hashing for deduplication

## Deduplication/Update Logic

### Payload Comparison Strategy
The system uses **SHA-256 hashing** to create deterministic payload signatures:

```java
// Canonical payload includes all business fields
{
  "eventId": "EVT001",
  "eventTime": "2024-01-14T10:30:00Z",
  "machineId": "M1",
  "durationMs": 1000,
  "defectCount": 5,
  "factoryId": "F1",
  "lineId": "L1"
}
```

### Winning Record Selection
The system implements a **"last received wins"** strategy:

1. **Same eventId + same payloadHash** → Deduplicate (ignore)
2. **Same eventId + different payloadHash** → Update with newer `receivedTime`
3. **New eventId** → Insert as new record

### Decision Flow
```
Event Received
    ↓
Check eventId exists?
    ├─ No → Insert
    └─ Yes → Compare payloadHash
           ├─ Same → Dedupe
           └─ Different → Compare receivedTime
                      ├─ Newer → Update
                      └─ Older → Ignore
```

## Thread Safety

### Current Implementation
The application achieves thread safety through **database-level constraints**:

1. **Primary Key Constraint**: `event_id` ensures no duplicate primary keys
2. **JPA/Hibernate Optimistic Locking**: `@Version` field prevents lost updates
3. **Transactional Semantics**: Each batch processed in single transaction
4. **Atomic Counters**: `AtomicInteger` for metrics tracking

### Thread Safety Mechanisms
```java
@Transactional
public BatchResponseDTO processEvents(List<EventDTO> events) {
    // All operations within single transaction
    // Database ensures ACID properties
    // Concurrent updates serialized by DB
}
```

### Limitations & Improvements
- **Current**: Relies on database serialization
- **Improvement**: Add application-level caching with distributed locks
- **Future**: Consider event streaming with Kafka for higher throughput

## Data Model

### Database Schema
```sql
CREATE TABLE machine_event (
    event_id VARCHAR(255) PRIMARY KEY,     -- Unique event identifier
    machine_id VARCHAR(255) NOT NULL,      -- Machine identifier
    factory_id VARCHAR(255) NOT NULL,      -- Factory identifier  
    line_id VARCHAR(255) NOT NULL,         -- Production line identifier
    event_time TIMESTAMP NOT NULL,          -- When event occurred
    received_time TIMESTAMP NOT NULL,       -- When system received event
    duration_ms BIGINT NOT NULL,            -- Event duration in milliseconds
    defect_count INTEGER NOT NULL,          -- Number of defects (can be negative)
    payload_hash VARCHAR(64) NOT NULL,      -- SHA-256 hash of payload
    created_at TIMESTAMP NOT NULL,          -- Record creation time
    updated_at TIMESTAMP NOT NULL           -- Record last update time
);

-- Indexes for performance
CREATE INDEX idx_machine_event_time ON machine_event(machine_id, event_time);
CREATE INDEX idx_factory_line_time ON machine_event(factory_id, line_id, event_time);
```

### In-Memory Structures
- **Event Processing**: Batch lists stored in memory during processing
- **Metrics Tracking**: `AtomicInteger` counters for real-time statistics
- **No Caching**: Direct database access ensures data consistency

## Performance Strategy

### Optimization Techniques

1. **Batch Processing**: Process events in batches to minimize database round trips
2. **Bulk Operations**: `saveAll()` for efficient bulk inserts/updates
3. **Database Indexing**: Strategic indexes on query columns
4. **Connection Pooling**: HikariCP for optimal connection management
5. **Async Processing**: Non-blocking I/O where applicable

### 1000 Events in 1 Second Target
To achieve the 1000 events/second target:

1. **Database Tuning**:
   ```properties
   spring.jpa.properties.hibernate.jdbc.batch_size=50
   spring.jpa.properties.hibernate.order_inserts=true
   spring.jpa.properties.hibernate.order_updates=true
   ```

2. **Application Tuning**:
   ```properties
   server.tomcat.threads.max=200
   spring.datasource.hikari.maximum-pool-size=50
   ```

3. **JVM Optimization**:
   ```bash
   -Xms2g -Xmx4g -XX:+UseG1GC
   ```

### Performance Bottlenecks
- **Database I/O**: Primary bottleneck for high-volume ingestion
- **Hash Computation**: SHA-256 calculation overhead
- **JSON Serialization**: EventDTO processing cost

## Edge Cases & Assumptions

### Design Decisions

1. **Negative Defect Counts**: 
   - **Decision**: Allow storage but filter in analytics
   - **Reason**: Preserve data integrity while providing meaningful metrics
   - **Tradeoff**: Additional filtering logic vs. data loss prevention

2. **Future Event Times**:
   - **Decision**: Reject events >15 minutes in future
   - **Reason**: Prevent clock skew and data integrity issues
   - **Tradeoff**: Strict validation vs. flexibility

3. **Duration Limits**:
   - **Decision**: Reject negative or >1 hour durations
   - **Reason**: Prevent data quality issues
   - **Tradeoff**: Validation overhead vs. data quality

4. **Idempotency**:
   - **Decision**: Hash-based deduplication
   - **Reason**: Handle network retries and duplicate submissions
   - **Tradeoff**: Storage overhead vs. reliability

### Assumptions
- **Clock Synchronization**: System clocks reasonably synchronized
- **Event Ordering**: `receivedTime` determines update precedence
- **Network Reliability**: Retry logic handled at client level
- **Data Volume**: Designed for moderate-high volume (not massive streaming)

## Setup & Run Instructions

### Prerequisites
- Java 17+
- Maven 3.6+
- PostgreSQL 13+ (production) or H2 (development)

### Local Development Setup

1. **Clone Repository**:
   ```bash
   git clone <repository-url>
   cd buyogo
   ```

2. **Configure Database**:
   ```properties
   # application.properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/buyogo
   spring.datasource.username=postgres
   spring.datasource.password=password
   ```

3. **Run Application**:
   ```bash
   mvn spring-boot:run
   ```

### Running Tests

1. **Unit Tests**:
   ```bash
   mvn test -Dtest=EventServiceTest,StateServiceTest
   ```

2. **Integration Tests**:
   ```bash
   mvn test -Dtest=EventProcessingIntegrationTest
   ```

3. **All Tests**:
   ```bash
   mvn test
   ```

### API Usage

1. **Ingest Events**:
   ```bash
   POST /api/events/batch
   Content-Type: application/json
   
   [
     {
       "eventId": "EVT001",
       "eventTime": "2024-01-14T10:30:00Z",
       "machineId": "M1",
       "factoryId": "F1", 
       "lineId": "L1",
       "durationMs": 1000,
       "defectCount": 5,
       "receivedTime": "2024-01-14T10:30:05Z"
     }
   ]
   ```

2. **Query Machine State**:
   ```bash
   GET /api/states/machines/{machineId}?start={timestamp}&end={timestamp}
   ```

## Future Improvements

### With More Time, I Would Implement:

1. **Caching Layer**:
   - Redis for frequently accessed machine states
   - Cache invalidation on event updates
   - Reduced database load for analytics queries

2. **Event Streaming**:
   - Apache Kafka for high-throughput event ingestion
   - Event sourcing for audit trail
   - Consumer groups for scalable processing

3. **Advanced Analytics**:
   - Time-series database (InfluxDB) for metrics
   - Real-time dashboards
   - Predictive analytics for defect patterns

4. **Performance Optimizations**:
   - Database sharding for horizontal scaling
   - Read replicas for analytics queries
   - Connection pooling optimization
   - Async processing with CompletableFuture

5. **Monitoring & Observability**:
   - Micrometer metrics collection
   - Distributed tracing with Zipkin
   - Health checks and circuit breakers
   - Performance profiling and optimization

6. **Security Enhancements**:
   - API authentication and authorization
   - Rate limiting for ingestion endpoints
   - Data encryption at rest and in transit
   - Audit logging for compliance

7. **Testing Improvements**:
   - Property-based testing with QuickCheck
   - Performance regression testing
   - Chaos engineering for resilience testing
   - Load testing with realistic data patterns

### Technical Debt
- Extract magic numbers to configuration
- Implement proper error handling strategies
- Add comprehensive logging and monitoring
- Improve test coverage for edge cases
- Document API contracts with OpenAPI/Swagger
