package edu.itc.shop.repository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import edu.itc.shop.domain.Order;
import edu.itc.shop.domain.OrderStatus;
import edu.itc.shop.web.dto.OrderSummary;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // ---- derived queries (parsed and validated at boot) ----
    List<Order> findByCustomerEmailAndStatusOrderByPlacedAtDesc(String email, OrderStatus status);

    long countByStatus(OrderStatus status);

    Optional<Order> findFirstByCustomerIdOrderByPlacedAtDesc(Long customerId);

    // ---- interface projection: only id, status, placed_at, customer.name are selected ----
    Page<OrderSummary> findByStatus(OrderStatus status, Pageable pageable);

    // ---- JPQL with fetch join: orders + lines + products in ONE statement ----
    @Query("""
        select distinct o from Order o
          join fetch o.lines l
          join fetch l.product
         where o.customer.email = :email
         order by o.placedAt desc
        """)
    List<Order> findWithLinesByCustomerEmail(@Param("email") String email);

    // ---- JPQL aggregate ----
    @Query("select count(l) from OrderLine l where l.order.status = :status")
    long countLinesByStatus(@Param("status") OrderStatus status);

    Slice<OrderSummary> findByIdIsNotNullOrderByPlacedAtDescIdDesc(Pageable pageable);

    @Query("""
        select o from Order o
         where o.placedAt < :afterAt or (o.placedAt = :afterAt and o.id < :afterId)
         order by o.placedAt desc, o.id desc
        """)
    Slice<OrderSummary> findFeedAfter(@Param("afterAt") Instant afterAt, @Param("afterId") Long afterId,
                                      Pageable pageable);

    Window<Order> findAllByOrderByPlacedAtDescIdDesc(ScrollPosition position, Limit limit);
}
