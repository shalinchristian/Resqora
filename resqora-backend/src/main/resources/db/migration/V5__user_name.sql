-- nullable so existing users (registered before names existed) stay valid
ALTER TABLE users ADD COLUMN name VARCHAR(100);
