CREATE TABLE dbo.products (
    id UNIQUEIDENTIFIER NOT NULL,
    name NVARCHAR(100) NOT NULL,
    description NVARCHAR(500) NULL,
    price DECIMAL(19, 2) NOT NULL,
    category NVARCHAR(100) NOT NULL,
    stock INT NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    updated_at DATETIME2(6) NOT NULL,
    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT ck_products_values CHECK (
        price >= 0 AND stock >= 0 AND status IN ('ACTIVE', 'INACTIVE')
    )
);

CREATE INDEX ix_products_created_at_id
    ON dbo.products (created_at DESC, id ASC);
