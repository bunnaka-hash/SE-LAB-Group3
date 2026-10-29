-- Fixed data for the 'test' profile. Every test context gets a fresh database,
-- so the generated ids are always 1, 2, 3, ... in the order below.

INSERT INTO product(name, price, stock) VALUES ('Wireless Mouse', 49.90, 10);   -- 1
INSERT INTO product(name, price, stock) VALUES ('USB-C Cable', 7.50, 150);      -- 2
INSERT INTO product(name, price, stock) VALUES ('Mouse Pad', 10.00, 20);        -- 3
INSERT INTO product(name, price, stock) VALUES ('Keyboard', 49.50, 80);         -- 4
INSERT INTO product(name, price, stock) VALUES ('Webcam', 9.95, 40);            -- 5

INSERT INTO customer(name, email) VALUES ('Sokha Chan', 'sokha@example.com');   -- 1
INSERT INTO customer(name, email) VALUES ('Dara Kim', 'dara@example.com');      -- 2
INSERT INTO customer(name, email) VALUES ('Vanna Lim', 'vanna@example.com');    -- 3, no orders

-- customer 1: one PAID and one NEW order; customer 2: one CANCELLED order (tax rate 0.10)
INSERT INTO orders(customer_id, status, placed_at, total)
VALUES (1, 'PAID', TIMESTAMP WITH TIME ZONE '2026-10-01 09:00:00+00', 120.78);  -- 1
INSERT INTO orders(customer_id, status, placed_at, total)
VALUES (1, 'NEW', TIMESTAMP WITH TIME ZONE '2026-10-02 09:00:00+00', 16.50);    -- 2
INSERT INTO orders(customer_id, status, placed_at, total)
VALUES (2, 'CANCELLED', TIMESTAMP WITH TIME ZONE '2026-10-03 09:00:00+00', 10.95); -- 3

INSERT INTO order_line(order_id, product_id, qty, unit_price) VALUES (1, 1, 2, 49.90);
INSERT INTO order_line(order_id, product_id, qty, unit_price) VALUES (1, 3, 1, 10.00);
INSERT INTO order_line(order_id, product_id, qty, unit_price) VALUES (2, 2, 2, 7.50);
INSERT INTO order_line(order_id, product_id, qty, unit_price) VALUES (3, 5, 1, 9.95);
