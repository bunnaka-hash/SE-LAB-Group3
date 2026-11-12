package edu.itc.shop.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import edu.itc.shop.repository.ProductRepository;

@SpringBootTest
@ActiveProfiles("test")
class BulkImportTest {
    private static final Logger log = LoggerFactory.getLogger(BulkImportTest.class);
    private static final int PRODUCT_COUNT = 50_000;

    @Autowired BulkImportService imports;
    @Autowired ProductRepository products;
    @TempDir Path tempDir;

    @Test
    void comparesThreeStrategiesForFiftyThousandProducts() throws Exception {
        Path statelessCsv = writeCsv("S" + suffix());
        Measurement stateless = measure(() -> imports.importCsv(statelessCsv));
        assertThat(stateless.rows()).isEqualTo(PRODUCT_COUNT);
        assertThat(stateless.millis()).as("StatelessSession import target").isLessThan(10_000);
        log.info("stateless insertMultiple(500): {} rows in {} ms, peak heap +{} MB, {} chunks",
                stateless.rows(), stateless.millis(), stateless.peakHeapMb(),
                (PRODUCT_COUNT + 499) / 500);

        Path noClearCsv = writeCsv("N" + suffix());
        Measurement noClear = measure(() -> imports.importWithSaveAll(noClearCsv, false));
        assertThat(noClear.rows()).isEqualTo(PRODUCT_COUNT);
        log.info("saveAll, one tx, no clear: {} rows in {} ms, peak heap +{} MB",
                noClear.rows(), noClear.millis(), noClear.peakHeapMb());

        Path clearCsv = writeCsv("C" + suffix());
        Measurement withClear = measure(() -> imports.importWithSaveAll(clearCsv, true));
        assertThat(withClear.rows()).isEqualTo(PRODUCT_COUNT);
        log.info("saveAll + flush/clear(500): {} rows in {} ms, peak heap +{} MB",
                withClear.rows(), withClear.millis(), withClear.peakHeapMb());

        assertThat(products.count()).isGreaterThanOrEqualTo(3L * PRODUCT_COUNT);
    }

    private Path writeCsv(String prefix) throws IOException {
        Path file = tempDir.resolve(prefix + ".csv");
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("sku;name;amount;currency;category;stock");
            writer.newLine();
            for (int i = 0; i < PRODUCT_COUNT; i++) {
                writer.write("%s-%05d;Bulk product %d;19.95;EUR;HARDWARE;100".formatted(prefix, i, i));
                writer.newLine();
            }
        }
        return file;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 6);
    }

    private static Measurement measure(ImportAction action) throws Exception {
        System.gc();
        Thread.sleep(100);
        PeakHeapSampler sampler = new PeakHeapSampler();
        long start = System.nanoTime();
        int rows;
        try (sampler) {
            rows = action.run();
        }
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        return new Measurement(rows, millis, sampler.peakHeapIncreaseMb());
    }

    @FunctionalInterface
    private interface ImportAction {
        int run() throws Exception;
    }

    private record Measurement(int rows, long millis, long peakHeapMb) { }

    private static final class PeakHeapSampler implements AutoCloseable {
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final long baselineBytes = heapUsed();
        private final AtomicLong peakBytes = new AtomicLong(baselineBytes);
        private final Thread sampler = Thread.ofPlatform().daemon().start(() -> {
            while (running.get()) {
                peakBytes.accumulateAndGet(heapUsed(), Math::max);
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });

        @Override
        public void close() throws InterruptedException {
            running.set(false);
            sampler.interrupt();
            sampler.join();
        }

        long peakHeapIncreaseMb() {
            return (peakBytes.get() - baselineBytes) / (1024 * 1024);
        }

        private static long heapUsed() {
            return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        }
    }
}
