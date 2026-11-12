package edu.itc.shop.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import edu.itc.shop.domain.OrderStatus;
import edu.itc.shop.service.OrderService;
import edu.itc.shop.web.dto.OrderSummary;
import edu.itc.shop.web.dto.OrderFeedResponse;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // GET /api/orders?status=PAID&page=0&size=5&sort=placedAt,desc
    @GetMapping
    public Page<OrderSummary> list(@RequestParam OrderStatus status,
                                   @PageableDefault(size = 20, sort = "placedAt",
                                                    direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.list(status, pageable);
    }

    @GetMapping("/feed")
    public OrderFeedResponse feed(@RequestParam(defaultValue = "20") int size,
                                  @RequestParam(required = false) String after,
                                  @RequestParam(defaultValue = "keyset") String strategy) {
        return orderService.feed(size, after, strategy);
    }
}
