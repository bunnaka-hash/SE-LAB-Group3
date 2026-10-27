package edu.itc.shop.service;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class ReportBuilder {
    private final StringBuilder sb = new StringBuilder();

    public ReportBuilder line(String s) {
        sb.append(s).append('\n');
        return this;
    }

    public String build() { return sb.toString(); }
}