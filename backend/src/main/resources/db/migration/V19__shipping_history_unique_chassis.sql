-- One row per chassis: remove duplicates (keep latest id), then enforce uniqueness.
DELETE sh
FROM shipping_history sh
INNER JOIN (
    SELECT chassis, MAX(id) AS keep_id
    FROM shipping_history
    GROUP BY chassis
) keeper ON sh.chassis = keeper.chassis AND sh.id <> keeper.keep_id;

CREATE UNIQUE INDEX ux_shipping_history_chassis ON shipping_history (chassis);
