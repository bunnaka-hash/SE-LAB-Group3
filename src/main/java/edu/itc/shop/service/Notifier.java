package edu.itc.shop.service;

public interface Notifier {
    void send(String to, String text);
}