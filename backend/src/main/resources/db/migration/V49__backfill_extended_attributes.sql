-- Phase 4: Backfill extended_attributes from legacy columns (dual-write baseline).
UPDATE purchases
SET extended_attributes = JSON_MERGE_PATCH(
    COALESCE(extended_attributes, JSON_OBJECT()),
    IF(options IS NOT NULL AND TRIM(options) <> '', JSON_OBJECT('options', options), JSON_OBJECT()),
    IF(auction_no IS NOT NULL AND TRIM(auction_no) <> '', JSON_OBJECT('auctionNo', auction_no), JSON_OBJECT()),
    IF(payment_date IS NOT NULL AND TRIM(payment_date) <> '', JSON_OBJECT('paymentDate', payment_date), JSON_OBJECT()),
    IF(notes IS NOT NULL AND TRIM(notes) <> '', JSON_OBJECT('notes', notes), JSON_OBJECT()),
    IF(venue_id IS NOT NULL AND TRIM(venue_id) <> '', JSON_OBJECT('venueId', venue_id), JSON_OBJECT()),
    IF(number_cut IS NOT NULL AND TRIM(number_cut) <> '', JSON_OBJECT('numberCut', number_cut), JSON_OBJECT()),
    IF(shaken IS NOT NULL, JSON_OBJECT('shaken', IF(shaken = 1, true, false)), JSON_OBJECT()),
    IF(negotiate IS NOT NULL, JSON_OBJECT('negotiate', IF(negotiate = 1, true, false)), JSON_OBJECT()),
    IF(is_package_mode IS NOT NULL, JSON_OBJECT('isPackageMode', IF(is_package_mode = 1, true, false)), JSON_OBJECT()),
    IF(car_pictures IS NOT NULL AND TRIM(car_pictures) <> '', JSON_OBJECT('carPictures', car_pictures), JSON_OBJECT())
)
WHERE extended_attributes IS NULL
   OR JSON_LENGTH(extended_attributes) = 0;
