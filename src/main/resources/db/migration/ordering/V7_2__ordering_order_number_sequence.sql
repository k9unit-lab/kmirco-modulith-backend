-- Counter behind the readable order number (prefix + counter padded to 6 digits).
-- It never restarts, even when the order number prefix changes.
CREATE SEQUENCE ordering.order_number_seq AS bigint START WITH 1 INCREMENT BY 1 NO MAXVALUE NO CYCLE;
