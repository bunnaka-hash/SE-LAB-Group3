package edu.itc.shop.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import edu.itc.shop.domain.Category;
import edu.itc.shop.domain.Money;
import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.ProductRepository;

@Service
public class BulkImportService {
    private static final int CHUNK_SIZE = 500;

    private final SessionFactory sessionFactory;
    private final ProductRepository products;
    private final EntityManager entityManager;

    public BulkImportService(EntityManagerFactory emf, ProductRepository products, EntityManager entityManager) {
        this.sessionFactory = emf.unwrap(SessionFactory.class);
        this.products = products;
        this.entityManager = entityManager;
    }

    public int importCsv(Path csv) throws IOException {
        int imported = 0;
        try (StatelessSession session = sessionFactory.openStatelessSession();
             Stream<String> lines = Files.lines(csv).skip(1)) {
            Transaction transaction = session.beginTransaction();
            try {
                session.setJdbcBatchSize(CHUNK_SIZE);
                List<Product> chunk = new ArrayList<>(CHUNK_SIZE);
                for (var iterator = lines.iterator(); iterator.hasNext();) {
                    chunk.add(parse(iterator.next()));
                    if (chunk.size() == CHUNK_SIZE) {
                        session.insertMultiple(chunk);
                        imported += chunk.size();
                        chunk.clear();
                    }
                }
                if (!chunk.isEmpty()) {
                    session.insertMultiple(chunk);
                    imported += chunk.size();
                }
                transaction.commit();
            } catch (RuntimeException | Error e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw e;
            }
        }
        return imported;
    }

    @Transactional
    public int importWithSaveAll(Path csv, boolean clearEveryChunk) throws IOException {
        int imported = 0;
        try (Stream<String> lines = Files.lines(csv).skip(1)) {
            if (!clearEveryChunk) {
                List<Product> all = lines.map(BulkImportService::parse).toList();
                products.saveAll(all);
                return all.size();
            }
            List<Product> chunk = new ArrayList<>(CHUNK_SIZE);
            for (var iterator = lines.iterator(); iterator.hasNext();) {
                chunk.add(parse(iterator.next()));
                if (chunk.size() == CHUNK_SIZE) {
                    products.saveAll(chunk);
                    entityManager.flush();
                    entityManager.clear();
                    imported += chunk.size();
                    chunk.clear();
                }
            }
            if (!chunk.isEmpty()) {
                products.saveAll(chunk);
                entityManager.flush();
                entityManager.clear();
                imported += chunk.size();
            }
        }
        return imported;
    }

    private static Product parse(String line) {
        String[] fields = line.split(";", -1);
        if (fields.length != 6) {
            throw new IllegalArgumentException("Expected 6 semicolon-delimited columns: " + line);
        }
        return new Product(fields[0], fields[1], new Money(new java.math.BigDecimal(fields[2]), fields[3]),
                Category.valueOf(fields[4]), Integer.parseInt(fields[5]));
    }
}
