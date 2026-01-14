package com.aditya.buyogo;

import com.aditya.buyogo.models.MachineEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class BuyogoApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void concurrentIngestion_shouldBeThreadSafe() throws Exception {
		int threads = 10;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch latch = new CountDownLatch(threads);

		for (int i = 0; i < threads; i++) {
			executor.submit(() -> {
				ingestionService.ingest(
						TestData.event("E-CONCURRENT", 5)
				);
				latch.countDown();
			});
		}

		latch.await();
		executor.shutdown();

		List<MachineEvent> events = repository.findAll();
		assertEquals(1, events.size());
	}


}
