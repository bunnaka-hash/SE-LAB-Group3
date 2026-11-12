package edu.itc.shop.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import edu.itc.shop.domain.Category;
import edu.itc.shop.domain.Money;
import edu.itc.shop.domain.Product;
import edu.itc.shop.domain.Tag;
import edu.itc.shop.web.dto.ProductReportRow;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ProductReportRepositoryTest {
    @Autowired TestEntityManager em;
    @Autowired ProductRepository products;
    @Autowired TagRepository tags;

    @Test
    void criteriaReportCombinesFiltersAndCountsTags() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String saleName = "sale-" + suffix;
        Tag sale = em.persist(new Tag(saleName));
        Tag featured = em.persist(new Tag("featured-" + suffix));
        Product match = new Product("R-KB-1", "Mechanical keyboard",
                Money.eur("129.00"), Category.HARDWARE, 4);
        match.tag(sale);
        match.tag(featured);
        em.persist(match);
        Product wrongTag = new Product("R-KB-2", "Keyboard case",
                Money.eur("39.00"), Category.HARDWARE, 12);
        wrongTag.tag(featured);
        em.persist(wrongTag);
        em.persist(new Product("R-BK-1", "Keyboard history",
                Money.eur("20.00"), Category.BOOKS, 4));
        em.flush();

        assertThat(tags.countByName(saleName)).isEqualTo(1);
        List<ProductReportRow> rows = products.findProductReport(
                "key", Category.HARDWARE, Money.eur("150.00").amount(), 1,
                saleName.toUpperCase(), "amount,desc");

        assertThat(rows).containsExactly(new ProductReportRow(
                "R-KB-1", "Mechanical keyboard", Money.eur("129.00").amount(), 4, 2L));
    }

    @Test
    void noTagFilterIncludesProductsWithoutTags() {
        em.persist(new Product("R-NO-TAG", "Plain product", Money.eur("5.00"), Category.TOYS, 0));
        em.flush();

        assertThat(products.findProductReport(null, Category.TOYS, null, null, null, null))
                .containsExactly(new ProductReportRow(
                        "R-NO-TAG", "Plain product", Money.eur("5.00").amount(), 0, 0L));
    }

    @Test
    void existingProductRepositoryProjectionsAndDerivedSortStillWork() {
        em.persist(new Product("R-SORT-1", "Cheap keyboard", Money.eur("10.00"), Category.HARDWARE, 1));
        em.persist(new Product("R-SORT-2", "Expensive keyboard", Money.eur("20.00"), Category.HARDWARE, 2));
        em.flush();

        assertThat(products.findTop3ByCategoryOrderByPriceAmountDesc(Category.HARDWARE))
                .extracting(Product::getSku)
                .containsSubsequence("R-SORT-2", "R-SORT-1");
        assertThat(products.findByCategory(Category.HARDWARE, PageRequest.of(0, 5)).getContent())
                .extracting(row -> row.sku())
                .contains("R-SORT-1", "R-SORT-2");
        assertThat(products.findAll(ProductSpecs.filter("keyboard", Category.HARDWARE,
                        Money.eur("15.00").amount(), true),
                        PageRequest.of(0, 5, Sort.by("sku"))).getContent())
                .extracting(Product::getSku)
                .containsExactly("R-SORT-1");
    }
}
