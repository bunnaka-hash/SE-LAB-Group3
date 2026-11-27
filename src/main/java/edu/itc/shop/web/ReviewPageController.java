package edu.itc.shop.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import edu.itc.shop.service.ReviewService;
import edu.itc.shop.web.dto.ReviewResponse;

/** Server-rendered page; reviews.html prints every field with th:text (HTML-escaped). */
@Controller
public class ReviewPageController {
    private final ReviewService service;

    public ReviewPageController(ReviewService service) { this.service = service; }

    @GetMapping("/products/{id}/reviews.html")
    public String page(@PathVariable("id") long id, Model model) {
        model.addAttribute("productId", id);
        model.addAttribute("reviews", service.forProduct(id).stream().map(ReviewResponse::from).toList());
        return "reviews";
    }
}
