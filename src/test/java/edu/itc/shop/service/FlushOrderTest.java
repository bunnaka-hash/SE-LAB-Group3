package edu.itc.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import edu.itc.shop.domain.Tag;
import edu.itc.shop.repository.TagRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional // each test method runs in one transaction and is rolled back at the end
class FlushOrderTest {

    @Autowired TagRepository tags;

    @Test
    void deleteThenInsertSameUniqueValueFailsBecauseInsertsRunBeforeDeletes() {
        Tag sale = tags.saveAndFlush(new Tag("sale"));

        assertThatThrownBy(() -> {
            tags.delete(sale);              // queued: the DELETE is not executed yet
            tags.save(new Tag("sale"));     // IDENTITY: INSERT runs now; SEQUENCE: at flush
            tags.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void flushBetweenDeleteAndInsertFixesIt() {
        Tag sale = tags.saveAndFlush(new Tag("sale"));

        tags.delete(sale);
        tags.flush();                       // the DELETE is executed now
        tags.save(new Tag("sale"));
        tags.flush();

        assertThat(tags.countByName("sale")).isEqualTo(1);
    }
}
