ALTER TABLE product ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_orders_feed ON orders (placed_at DESC, id DESC);
CREATE INDEX idx_product_category_amount ON product (category, amount);
CREATE INDEX idx_product_tag_tag_product ON product_tag (tag_id, product_id);
