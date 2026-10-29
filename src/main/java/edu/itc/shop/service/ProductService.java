package edu.itc.shop.service;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service 
public class ProductService {
    private final ProductRepository repository;

    public ProductService (ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional 
    public void restock (
        long fromId,
        long toId,
        int qty) {
            int first = repository.updateStock (fromId, -qty);

            if (first ==0) {
                throw new IllegalStateException(
                    "Source product not found");
        }

        int second =
                repository.updateStock(toId, qty);

        if (second == 0) {
            throw new IllegalStateException(
                    "Destination product not found");
        }
            }
        }
    

