package edu.itc.shop.web;

import edu.itc.shop.config.ShopProperties;
import edu.itc.shop.domain.Product;
import edu.itc.shop.service.ProductService;
import edu.itc.shop.web.dto.CreateProduct;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/products")
public class ProductPageController {

    private static final Logger log = LoggerFactory.getLogger(ProductPageController.class);

    private final ProductService productService;
    private final ShopProperties props;

    public ProductPageController(ProductService productService, ShopProperties props) {
        this.productService = productService;
        this.props = props;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, Model model) {
        List<Product> found = (q == null || q.isBlank())
                ? productService.findAll()
                : productService.search(q.trim());

        model.addAttribute("products",
                found.stream().map(p -> ProductView.from(p, props.taxRate())).toList());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("currency", props.currency());
        model.addAttribute("threshold", props.lowStockThreshold());
        return "products/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("productForm", new ProductForm());
        model.addAttribute("currency", props.currency());
        return "products/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("productForm") ProductForm form,
                         BindingResult errors,
                         Model model,
                         RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            log.warn("product validation failed: {} error(s) on fields {}",
                    errors.getErrorCount(),
                    errors.getFieldErrors().stream().map(FieldError::getField).toList());
            model.addAttribute("currency", props.currency());
            return "products/form";
        }

        Product saved = productService.create(
                new CreateProduct(form.getName(), form.getPrice(), form.getStock()));

        redirect.addFlashAttribute("message", "Created product '" + saved.name() + "'");
        return "redirect:/products";
    }
}