-- src/main/resources/db/migration/V1__init.sql
CREATE SEQUENCE product_seq    START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE customer_seq   START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE tag_seq        START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE orders_seq     START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE order_line_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE product (
    id       BIGINT        PRIMARY KEY,
    sku      VARCHAR(20)   NOT NULL UNIQUE,
    name     VARCHAR(120)  NOT NULL,
    amount   DECIMAL(12,2) NOT NULL,
    currency CHAR(3)       NOT NULL,
    category VARCHAR(20)   NOT NULL,
    stock    INT           NOT NULL DEFAULT 0 CHECK (stock >= 0)
);

CREATE TABLE customer (
    id          BIGINT       PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(120) NOT NULL UNIQUE,
    street      VARCHAR(255),
    city        VARCHAR(255),
    zip         VARCHAR(255),
    ship_street VARCHAR(255),
    ship_city   VARCHAR(255),
    ship_zip    VARCHAR(255)
);

CREATE TABLE tag (
    id   BIGINT      PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE product_tag (
    product_id BIGINT NOT NULL REFERENCES product (id),
    tag_id     BIGINT NOT NULL REFERENCES tag (id),
    PRIMARY KEY (product_id, tag_id)
);

CREATE TABLE orders (
    id          BIGINT                   PRIMARY KEY,
    customer_id BIGINT                   NOT NULL REFERENCES customer (id),
    status      VARCHAR(20)              NOT NULL,
    placed_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    version     BIGINT                   NOT NULL DEFAULT 0
);

CREATE TABLE order_line (
    id         BIGINT        PRIMARY KEY,
    order_id   BIGINT        NOT NULL REFERENCES orders (id),
    product_id BIGINT        NOT NULL REFERENCES product (id),
    quantity   INT           NOT NULL CHECK (quantity > 0),
    amount     DECIMAL(12,2) NOT NULL,
    currency   CHAR(3)       NOT NULL
);
