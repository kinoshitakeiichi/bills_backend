-- V43.1 为当前分类主数据批量分配 Lucide icon_code。可重复执行。
BEGIN;

UPDATE bills.b_categories
SET icon_code = 'circle-minus', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'EXPENSE_ROOT';

UPDATE bills.b_categories
SET icon_code = 'utensils', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'FOOD';

UPDATE bills.b_categories
SET icon_code = 'store', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'FOOD_CONVENIENCE';

UPDATE bills.b_categories
SET icon_code = 'shopping-basket', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'FOOD_GROCERY';

UPDATE bills.b_categories
SET icon_code = 'utensils', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'FOOD_DINING';

UPDATE bills.b_categories
SET icon_code = 'bike', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'FOOD_DELIVERY';

UPDATE bills.b_categories
SET icon_code = 'home', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'HOUSING';

UPDATE bills.b_categories
SET icon_code = 'key-round', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'HOUSING_RENT';

UPDATE bills.b_categories
SET icon_code = 'building-2', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'HOUSING_MANAGEMENT';

UPDATE bills.b_categories
SET icon_code = 'plug-zap', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'HOUSING_UTILITIES';

UPDATE bills.b_categories
SET icon_code = 'wifi', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'HOUSING_COMMUNICATION';

UPDATE bills.b_categories
SET icon_code = 'wrench', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'HOUSING_MAINTENANCE';

UPDATE bills.b_categories
SET icon_code = 'package', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'HOUSING_HOUSEHOLD';

UPDATE bills.b_categories
SET icon_code = 'shield-check', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'HOUSING_INSURANCE';

UPDATE bills.b_categories
SET icon_code = 'house-plus', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'HOUSING_PURCHASE';

UPDATE bills.b_categories
SET icon_code = 'car', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'TRANSPORT';

UPDATE bills.b_categories
SET icon_code = 'train-front', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'TRANSPORT_PUBLIC';

UPDATE bills.b_categories
SET icon_code = 'badge-check', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'TRANSPORT_LICENSE';

UPDATE bills.b_categories
SET icon_code = 'heart-pulse', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'HEALTH';

UPDATE bills.b_categories
SET icon_code = 'hospital', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'HEALTH_MEDICAL';

UPDATE bills.b_categories
SET icon_code = 'badge-plus', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'HEALTH_DENTAL';

UPDATE bills.b_categories
SET icon_code = 'accessibility', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'HEALTH_ORTHOPEDIC';

UPDATE bills.b_categories
SET icon_code = 'pill', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'HEALTH_RX';

UPDATE bills.b_categories
SET icon_code = 'pill-bottle', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'HEALTH_OTC';

UPDATE bills.b_categories
SET icon_code = 'dumbbell', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'HEALTH_FITNESS';

UPDATE bills.b_categories
SET icon_code = 'graduation-cap', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'EDUCATION';

UPDATE bills.b_categories
SET icon_code = 'languages', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'EDUCATION_LANGUAGE';

UPDATE bills.b_categories
SET icon_code = 'music', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'EDUCATION_MUSIC';

UPDATE bills.b_categories
SET icon_code = 'shirt', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'PERSONAL';

UPDATE bills.b_categories
SET icon_code = 'shirt', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'PERSONAL_CLOTHING';

UPDATE bills.b_categories
SET icon_code = 'scissors', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'PERSONAL_GROOMING';

UPDATE bills.b_categories
SET icon_code = 'gamepad-2', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'ENTERTAINMENT';

UPDATE bills.b_categories
SET icon_code = 'party-popper', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'ENTERTAINMENT_GENERAL';

UPDATE bills.b_categories
SET icon_code = 'tent-tree', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'ENTERTAINMENT_OUTDOOR';

UPDATE bills.b_categories
SET icon_code = 'palette', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'ART_SUPPLIES';

UPDATE bills.b_categories
SET icon_code = 'music-2', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'MUSICAL_INSTRUMENTS';

UPDATE bills.b_categories
SET icon_code = 'plane', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'TRAVEL';

UPDATE bills.b_categories
SET icon_code = 'plane-takeoff', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'TRAVEL_TRANSPORT';

UPDATE bills.b_categories
SET icon_code = 'bed-double', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'TRAVEL_LODGING';

UPDATE bills.b_categories
SET icon_code = 'utensils', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'TRAVEL_FOOD';

UPDATE bills.b_categories
SET icon_code = 'shopping-bag', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'TRAVEL_SHOPPING';

UPDATE bills.b_categories
SET icon_code = 'luggage', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'TRAVEL_OTHER';

UPDATE bills.b_categories
SET icon_code = 'monitor-smartphone', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'DIGITAL';

UPDATE bills.b_categories
SET icon_code = 'app-window', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'DIGITAL_SOFTWARE';

UPDATE bills.b_categories
SET icon_code = 'play-square', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'DIGITAL_CONTENT';

UPDATE bills.b_categories
SET icon_code = 'badge', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'DIGITAL_MEMBERSHIP';

UPDATE bills.b_categories
SET icon_code = 'shopping-bag', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'SHOPPING';

UPDATE bills.b_categories
SET icon_code = 'plug', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'SHOPPING_APPLIANCE';

UPDATE bills.b_categories
SET icon_code = 'camera', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'SHOPPING_CAMERA';

UPDATE bills.b_categories
SET icon_code = 'gem', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'SHOPPING_LUXURY';

UPDATE bills.b_categories
SET icon_code = 'backpack', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'SHOPPING_OUTDOOR';

UPDATE bills.b_categories
SET icon_code = 'package', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'SHOPPING_OTHER';

UPDATE bills.b_categories
SET icon_code = 'landmark', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'TAX_INSURANCE';

UPDATE bills.b_categories
SET icon_code = 'receipt-text', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'TAX_RESIDENT';

UPDATE bills.b_categories
SET icon_code = 'wallet-cards', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'SOCIAL_PENSION';

UPDATE bills.b_categories
SET icon_code = 'shield-plus', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'SOCIAL_HEALTH_INSURANCE';

UPDATE bills.b_categories
SET icon_code = 'file-text', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'TAX_TOTAL';

UPDATE bills.b_categories
SET icon_code = 'receipt', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'FEES';

UPDATE bills.b_categories
SET icon_code = 'landmark', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'FEES_ATM';

UPDATE bills.b_categories
SET icon_code = 'badge-dollar-sign', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'FEES_FINANCIAL';

UPDATE bills.b_categories
SET icon_code = 'package-open', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'FEES_POSTAGE';

UPDATE bills.b_categories
SET icon_code = 'ellipsis', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'OTHER_EXPENSE';

UPDATE bills.b_categories
SET icon_code = 'wallet-cards', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'OTHER_TOPUP';

UPDATE bills.b_categories
SET icon_code = 'archive', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'HISTORICAL_UNRESOLVED';

UPDATE bills.b_categories
SET icon_code = 'credit-card', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'HIST_MISSING_CREDIT_PURCHASE';

UPDATE bills.b_categories
SET icon_code = 'circle-plus', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'INCOME_ROOT';

UPDATE bills.b_categories
SET icon_code = 'briefcase-business', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'EMPLOYMENT_INCOME';

UPDATE bills.b_categories
SET icon_code = 'wallet', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'SALARY';

UPDATE bills.b_categories
SET icon_code = 'gift', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'BONUS';

UPDATE bills.b_categories
SET icon_code = 'receipt-japanese-yen', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'REIMBURSEMENT';

UPDATE bills.b_categories
SET icon_code = 'hand-coins', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'REIMBURSEMENT_EXPENSE';

UPDATE bills.b_categories
SET icon_code = 'circle-dollar-sign', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'OTHER_INCOME';

UPDATE bills.b_categories
SET icon_code = 'percent', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'INTEREST_INCOME';

UPDATE bills.b_categories
SET icon_code = 'sparkles', updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'OTHER_INCOME_MISC';

COMMIT;
