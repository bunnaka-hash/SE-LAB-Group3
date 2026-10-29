-- Seed data for the 'prod' profile. ON CONFLICT DO NOTHING makes it safe to run on every start.

INSERT INTO product(name, price, stock) VALUES ('Laptop', 1200.00, 10) ON CONFLICT DO NOTHING;
INSERT INTO product(name, price, stock) VALUES ('Mouse', 25.50, 50) ON CONFLICT DO NOTHING;
INSERT INTO product(name, price, stock) VALUES ('Keyboard', 49.99, 30) ON CONFLICT DO NOTHING;
INSERT INTO product(name, price, stock) VALUES ('Monitor', 299.99, 15) ON CONFLICT DO NOTHING;
INSERT INTO product(name, price, stock) VALUES ('Headphones', 89.99, 20) ON CONFLICT DO NOTHING;

INSERT INTO customer(name, email) VALUES ('Sokha Chan', 'sokha@example.com') ON CONFLICT DO NOTHING;
INSERT INTO customer(name, email) VALUES ('Dara Kim', 'dara@example.com') ON CONFLICT DO NOTHING;
INSERT INTO customer(name, email) VALUES ('Vanna Lim', 'vanna@example.com') ON CONFLICT DO NOTHING;
