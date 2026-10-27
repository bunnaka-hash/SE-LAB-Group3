package edu.itc.shop;

import edu.itc.shop.service.ReportBuilder;
import edu.itc.shop.service.WelcomeService;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ShopApplication {
    private static final Logger log = LoggerFactory.getLogger(ShopApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }

    @Bean
    CommandLineRunner demo(ApplicationContext ctx,
                           WelcomeService welcome,
                           ObjectProvider<ReportBuilder> reports) {
        return args -> {
            List<String> notifiers = Arrays.stream(ctx.getBeanDefinitionNames())
                    .filter(name -> name.contains("Notifier"))
                    .sorted()
                    .toList();
            log.info("{} beans; notifiers = {}", ctx.getBeanDefinitionCount(), notifiers);

            welcome.welcome("ann", false);   // default -> EmailNotifier
            welcome.welcome("bob", true);    // urgent  -> SmsNotifier

            ReportBuilder r1 = reports.getObject();
            ReportBuilder r2 = reports.getObject();
            log.info("prototype instances differ: {}",
                    System.identityHashCode(r1) != System.identityHashCode(r2));
        };
    }
}