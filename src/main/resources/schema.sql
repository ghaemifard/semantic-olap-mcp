CREATE TABLE IF NOT EXISTS public.dim_time (
                                               time_key     INT PRIMARY KEY,
                                               year_key     INT,
                                               year_name    VARCHAR(10),
    quarter_key  INT,
    quarter_name VARCHAR(10),
    month_key    INT,
    month_name   VARCHAR(20)
    );

CREATE TABLE IF NOT EXISTS public.dim_product (
                                                  product_key   INT PRIMARY KEY,
                                                  category_key  INT,
                                                  category_name VARCHAR(100),
    product_name  VARCHAR(200)
    );

CREATE TABLE IF NOT EXISTS public.fact_sales (
                                                 sales_id   BIGSERIAL PRIMARY KEY,
                                                 time_key   INT REFERENCES public.dim_time(time_key),
    product_key INT REFERENCES public.dim_product(product_key),
    order_id   BIGINT,
    amount     NUMERIC(18,2),
    qty        INT
    );

-- minimal seed data
INSERT INTO public.dim_time (time_key, year_key, year_name, quarter_key, quarter_name, month_key, month_name)
VALUES
    (202301, 2023, '2023', 20231, '2023-Q1', 202301, '2023-01'),
    (202302, 2023, '2023', 20231, '2023-Q1', 202302, '2023-02'),
    (202401, 2024, '2024', 20241, '2024-Q1', 202401, '2024-01')
    ON CONFLICT DO NOTHING;

INSERT INTO public.dim_product (product_key, category_key, category_name, product_name)
VALUES
    (1, 10, 'Electronics', 'Laptop'),
    (2, 10, 'Electronics', 'Metal Detector'),
    (3, 20, 'Books', 'Atlas Of The Heart')
    ON CONFLICT DO NOTHING;

INSERT INTO public.fact_sales (time_key, product_key, order_id, amount, qty)
VALUES
    (202301, 1, 1001, 1200.00, 1),
    (202301, 2, 1002,  800.00, 2),
    (202302, 1, 1003, 1500.00, 1),
    (202401, 3, 1004,   45.00, 3),
    (202401, 2, 1005,  900.00, 1)
    ON CONFLICT DO NOTHING;