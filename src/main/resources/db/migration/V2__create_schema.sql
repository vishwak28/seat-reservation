-- A show: a hall of numbered seats on sale at a fixed price (integer paise).
CREATE TABLE shows (
                       id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       name           TEXT        NOT NULL,
                       price_paise    BIGINT      NOT NULL CHECK (price_paise >= 0),
                       per_user_limit INT         NOT NULL DEFAULT 4 CHECK (per_user_limit > 0),
                       total_seats    INT         NOT NULL CHECK (total_seats > 0),
                       created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per successful reservation request. The idempotency key is here.
CREATE TABLE reservations (
                              id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              show_id         UUID        NOT NULL REFERENCES shows (id),
                              user_id         TEXT        NOT NULL,
                              amount_paise    BIGINT      NOT NULL CHECK (amount_paise >= 0),
                              status          TEXT        NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
                              idempotency_key TEXT        NOT NULL,
                              request_hash    TEXT        NOT NULL,
                              created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                              cancelled_at    TIMESTAMPTZ,
                              CONSTRAINT uq_reservation_idempotency UNIQUE (user_id, idempotency_key)
);

-- One row per physical seat. The atomic decision happens on this row.
CREATE TABLE seats (
                       show_id        UUID NOT NULL REFERENCES shows (id),
                       seat_no        TEXT NOT NULL,
                       status         TEXT NOT NULL DEFAULT 'AVAILABLE'
                           CHECK (status IN ('AVAILABLE', 'HELD', 'CONFIRMED')),
                       reservation_id UUID REFERENCES reservations (id),
                       PRIMARY KEY (show_id, seat_no),
                       CONSTRAINT ck_seat_owner CHECK ((status = 'AVAILABLE') = (reservation_id IS NULL))
);

CREATE INDEX idx_seats_reservation ON seats (reservation_id);

-- Per-user, per-show seat counter, used to enforce the booking limit atomically.
CREATE TABLE user_show_holds (
                                 show_id    UUID NOT NULL REFERENCES shows (id),
                                 user_id    TEXT NOT NULL,
                                 seat_count INT  NOT NULL CHECK (seat_count >= 0),
                                 PRIMARY KEY (show_id, user_id)
);