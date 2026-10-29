package edu.itc.shop;

import edu.itc.shop.service.ReportBuilder;
import edu.itc.shop.service.WelcomeService;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class DemoRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoRunner.class);

    private final ApplicationContext context;
    private final WelcomeService welcome;
    private final ObjectProvider<ReportBuilder> reports;

    public DemoRunner(ApplicationContext context,
                      WelcomeService welcome,
                      ObjectProvider<ReportBuilder> reports) {
        this.context = context;
        this.welcome = welcome;
        this.reports = reports;
    }

    @Override
    public void run(String... args) {
        List<String> notifiers = Arrays.stream(context.getBeanDefinitionNames())
                .filter(name -> name.contains("Notifier"))
                .sorted()
                .toList();
        log.info("{} beans; notifiers = {}", context.getBeanDefinitionCount(), notifiers);

        welcome.welcome("ann", false);
        welcome.welcome("bob", true);

        ReportBuilder first = reports.getObject();
        ReportBuilder second = reports.getObject();
        log.info("prototype instances differ: {}",
                System.identityHashCode(first) != System.identityHashCode(second));
    }
}
