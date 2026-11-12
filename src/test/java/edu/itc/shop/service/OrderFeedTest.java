package edu.itc.shop.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import edu.itc.shop.support.TestData;

@SpringBootTest
@ActiveProfiles("test")
class OrderFeedTest {
    @Autowired OrderService orders;
    @Autowired TestData data;

    @Test
    void sliceAndKeysetExposeTheSameCursorFeedWithoutDuplicates() {
        data.newOrder();
        data.newOrder();
        data.newOrder();

        var keyset = orders.feed(2, null, "keyset");
        var slice = orders.feed(2, null, "slice");

        assertThat(keyset.items()).hasSize(2);
        assertThat(keyset.items()).extracting(item -> item.id())
                .containsExactlyElementsOf(slice.items().stream().map(item -> item.id()).toList());
        assertThat(keyset.hasNext()).isTrue();
        assertThat(keyset.nextCursor()).contains(",");

        var next = orders.feed(2, keyset.nextCursor(), "keyset");
        assertThat(next.items()).extracting(item -> item.id())
                .doesNotContainAnyElementsOf(keyset.items().stream().map(item -> item.id()).toList());
    }
}
