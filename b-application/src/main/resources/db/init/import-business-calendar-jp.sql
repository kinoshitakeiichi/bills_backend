\set ON_ERROR_STOP on
BEGIN;
-- Japan business calendar master seed.
-- Source: legacy holidays.csv supplied for migration.
--
-- Imported rows:
--   country_code = JP only (108 official/red holidays, 2022-01-01 .. 2027-11-23)
--
-- Deliberately NOT imported:
--   XX = weekends, because BusinessCalendarService already treats Saturday/Sunday as non-business days.
--   XY = EPOS-specific dates, because those are card-specific historical exceptions, not Japan-wide holidays.
--
-- Idempotent: safe to execute on every application startup.

INSERT INTO bills.b_business_calendars (
    calendar_id,
    calendar_code,
    calendar_name,
    country_code,
    enabled,
    created_at,
    updated_at
)
VALUES (
    '30000000-0000-0000-0000-000000000001',
    'JP',
    '日本营业日日历',
    'JP',
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (calendar_code) DO UPDATE
SET calendar_name = EXCLUDED.calendar_name,
    country_code = EXCLUDED.country_code,
    enabled = TRUE,
    updated_at = NOW();

-- Rebuild only rows owned by this seed source; manually maintained overrides from other sources are preserved.
DELETE FROM bills.b_business_calendar_days d
USING bills.b_business_calendars c
WHERE d.calendar_id = c.calendar_id
  AND c.calendar_code = 'JP'
  AND d.source = 'LEGACY_HOLIDAYS_CSV';

WITH calendar AS (
    SELECT calendar_id
    FROM bills.b_business_calendars
    WHERE calendar_code = 'JP'
),
seed_day(calendar_day_id, calendar_date, business_day, holiday_name, source) AS (
VALUES
  ('fc7bc694-d093-558f-af88-64273c00da74', '2022-01-01', FALSE, '元日', 'LEGACY_HOLIDAYS_CSV'),
  ('8ced821e-e4ee-52c0-89b8-62b330c705a0', '2022-01-10', FALSE, '成人の日', 'LEGACY_HOLIDAYS_CSV'),
  ('67c2165b-8fbd-5673-851b-b634cb8606ca', '2022-02-11', FALSE, '建国記念の日', 'LEGACY_HOLIDAYS_CSV'),
  ('e7a5b4b9-d5fe-5174-b47b-677debf93303', '2022-02-23', FALSE, '天皇誕生日', 'LEGACY_HOLIDAYS_CSV'),
  ('4fdba61f-5422-5cac-8d83-ee131f91dc80', '2022-03-21', FALSE, '春分の日', 'LEGACY_HOLIDAYS_CSV'),
  ('bc4d7c32-204f-57df-a0d5-f345f581615b', '2022-04-29', FALSE, '昭和の日', 'LEGACY_HOLIDAYS_CSV'),
  ('740a2808-7645-50f3-abe5-acee473d2df9', '2022-05-03', FALSE, '憲法記念日', 'LEGACY_HOLIDAYS_CSV'),
  ('2cde1439-a7ac-5a7c-a5fc-ab3baeaea2fe', '2022-05-04', FALSE, 'みどりの日', 'LEGACY_HOLIDAYS_CSV'),
  ('45176ac1-33de-5c36-823f-960b3956baff', '2022-05-05', FALSE, 'こどもの日', 'LEGACY_HOLIDAYS_CSV'),
  ('14b74005-b653-5b5b-b86c-8c9cd2d90afe', '2022-07-18', FALSE, '海の日', 'LEGACY_HOLIDAYS_CSV'),
  ('74ce48a8-a86a-5dda-ab8e-8561d520ecc8', '2022-08-11', FALSE, '山の日', 'LEGACY_HOLIDAYS_CSV'),
  ('1dcc7ab6-4e4c-57b3-9505-0714e60cb8af', '2022-09-19', FALSE, '敬老の日', 'LEGACY_HOLIDAYS_CSV'),
  ('d4fc663f-acdf-5bac-9eeb-203e02e147a8', '2022-09-23', FALSE, '秋分の日', 'LEGACY_HOLIDAYS_CSV'),
  ('21a6b462-5511-508a-b096-e5d65afac3e2', '2022-10-10', FALSE, 'スポーツの日', 'LEGACY_HOLIDAYS_CSV'),
  ('d2ec81a4-928c-5b8d-9ba7-cf8a5338dcd2', '2022-11-03', FALSE, '文化の日', 'LEGACY_HOLIDAYS_CSV'),
  ('0728ffb4-b80a-5543-b88d-8809cc04d404', '2022-11-23', FALSE, '勤労感謝の日', 'LEGACY_HOLIDAYS_CSV'),
  ('cc730ab1-aef8-5e69-b6a8-931d52728a20', '2023-01-01', FALSE, '元日', 'LEGACY_HOLIDAYS_CSV'),
  ('27d56a83-d985-535b-9cea-2967f3f0bacc', '2023-01-02', FALSE, '休日 元日', 'LEGACY_HOLIDAYS_CSV'),
  ('15485304-03c4-5b97-8cfc-0e2c0561269b', '2023-01-09', FALSE, '成人の日', 'LEGACY_HOLIDAYS_CSV'),
  ('e1c00936-e209-5c0c-a52f-93f1c17a9af4', '2023-02-11', FALSE, '建国記念の日', 'LEGACY_HOLIDAYS_CSV'),
  ('03859122-0ceb-5985-b375-c8c48dc2cfc1', '2023-02-23', FALSE, '天皇誕生日', 'LEGACY_HOLIDAYS_CSV'),
  ('58369ad9-21be-54a9-890f-0ad57c144e81', '2023-03-21', FALSE, '春分の日', 'LEGACY_HOLIDAYS_CSV'),
  ('22b56680-1c59-5806-842b-5ab5a7ff02f6', '2023-04-29', FALSE, '昭和の日', 'LEGACY_HOLIDAYS_CSV'),
  ('d71a7a04-e9c4-5392-86bf-6867901c5a8b', '2023-05-03', FALSE, '憲法記念日', 'LEGACY_HOLIDAYS_CSV'),
  ('27e1f988-36aa-5cf9-befe-2fe51368a8e5', '2023-05-04', FALSE, 'みどりの日', 'LEGACY_HOLIDAYS_CSV'),
  ('13ff2f3f-4936-51e9-bb63-a05d120a9cb2', '2023-05-05', FALSE, 'こどもの日', 'LEGACY_HOLIDAYS_CSV'),
  ('82ba5bb7-106f-5c75-bf75-80bfb25ff83c', '2023-07-17', FALSE, '海の日', 'LEGACY_HOLIDAYS_CSV'),
  ('ded1a954-fa06-5e80-bb4b-89c86e2583e7', '2023-08-11', FALSE, '山の日', 'LEGACY_HOLIDAYS_CSV'),
  ('9122697d-6841-5627-9f9c-f94ab6566938', '2023-09-18', FALSE, '敬老の日', 'LEGACY_HOLIDAYS_CSV'),
  ('a107171e-6183-51b8-9ba0-f27e06035e36', '2023-09-23', FALSE, '秋分の日', 'LEGACY_HOLIDAYS_CSV'),
  ('1c649770-ea43-5eeb-8f49-28e80fe996df', '2023-10-09', FALSE, 'スポーツの日', 'LEGACY_HOLIDAYS_CSV'),
  ('90beb509-0434-5139-bf45-4b50e02161cd', '2023-11-03', FALSE, '文化の日', 'LEGACY_HOLIDAYS_CSV'),
  ('f7c5848e-750a-59ad-9c60-c13028a10c0e', '2023-11-23', FALSE, '勤労感謝の日', 'LEGACY_HOLIDAYS_CSV'),
  ('f6ec94d6-81e4-5672-a1d2-fb8f13948168', '2024-01-01', FALSE, '元日', 'LEGACY_HOLIDAYS_CSV'),
  ('89d2a0ac-6986-5c57-be5a-fe3eed802f90', '2024-01-08', FALSE, '成人の日', 'LEGACY_HOLIDAYS_CSV'),
  ('64b8c504-eab9-5f68-9534-41c94a9bc3b0', '2024-02-11', FALSE, '建国記念の日', 'LEGACY_HOLIDAYS_CSV'),
  ('83b3b5fe-1f4f-5de1-b38d-66af7808f0f0', '2024-02-12', FALSE, '建国記念の日 振替休日', 'LEGACY_HOLIDAYS_CSV'),
  ('ede978f1-f076-5713-972c-261df52455f0', '2024-02-23', FALSE, '天皇誕生日', 'LEGACY_HOLIDAYS_CSV'),
  ('22fd9e31-a112-53ef-b235-535a31151f53', '2024-03-20', FALSE, '春分の日', 'LEGACY_HOLIDAYS_CSV'),
  ('8faa7077-7d14-5cc3-b580-f4209ab3b73e', '2024-04-29', FALSE, '昭和の日', 'LEGACY_HOLIDAYS_CSV'),
  ('538d45cf-618f-5590-bfca-539f8773069b', '2024-05-03', FALSE, '憲法記念日', 'LEGACY_HOLIDAYS_CSV'),
  ('fa9a4ad3-9637-53c9-b929-f32aae6a44ac', '2024-05-04', FALSE, 'みどりの日', 'LEGACY_HOLIDAYS_CSV'),
  ('c8e73ac7-a8bc-501e-b589-1fecafbb34a2', '2024-05-05', FALSE, 'こどもの日', 'LEGACY_HOLIDAYS_CSV'),
  ('ac860a1b-e6bf-565a-bb8d-2384fe423ca0', '2024-05-06', FALSE, 'こどもの日 振替休日', 'LEGACY_HOLIDAYS_CSV'),
  ('073e24cb-8847-53ab-b00e-fe7983a2124a', '2024-07-15', FALSE, '海の日', 'LEGACY_HOLIDAYS_CSV'),
  ('6684f1b7-db65-5a06-bfa7-e869b08b3784', '2024-08-11', FALSE, '山の日', 'LEGACY_HOLIDAYS_CSV'),
  ('16a755f9-c41e-5c40-8ba1-4ca1af1e7ff4', '2024-08-12', FALSE, '休日 山の日', 'LEGACY_HOLIDAYS_CSV'),
  ('7df4c8d8-d438-56e7-ada6-a5fbd90033f5', '2024-09-16', FALSE, '敬老の日', 'LEGACY_HOLIDAYS_CSV'),
  ('d9e65328-a36c-547c-b1b1-7e57592c3b6a', '2024-09-22', FALSE, '秋分の日', 'LEGACY_HOLIDAYS_CSV'),
  ('0abbcaf0-69ba-5c3c-80ac-3bff98b89627', '2024-09-23', FALSE, '秋分の日 振替休日', 'LEGACY_HOLIDAYS_CSV'),
  ('61d018c5-a47c-56ab-9756-1f64a1836f36', '2024-10-14', FALSE, 'スポーツの日', 'LEGACY_HOLIDAYS_CSV'),
  ('3d553491-7309-561b-9ded-e66e2e73232a', '2024-11-03', FALSE, '文化の日', 'LEGACY_HOLIDAYS_CSV'),
  ('7beaf987-5224-5b74-b8ea-a8d956108261', '2024-11-04', FALSE, '文化の日 振替休日', 'LEGACY_HOLIDAYS_CSV'),
  ('2890643d-9f73-56ea-abaf-20eeeef6eb88', '2024-11-23', FALSE, '勤労感謝の日', 'LEGACY_HOLIDAYS_CSV'),
  ('77360221-33d5-5dc3-bd80-3d4c9922e9c7', '2025-01-01', FALSE, '元日', 'LEGACY_HOLIDAYS_CSV'),
  ('647cc459-e3a8-577e-886b-690b6dce4569', '2025-01-13', FALSE, '成人の日', 'LEGACY_HOLIDAYS_CSV'),
  ('8bd757b6-5440-50c6-a05d-e0411d178d9e', '2025-02-11', FALSE, '建国記念の日', 'LEGACY_HOLIDAYS_CSV'),
  ('6981a925-8f35-5cfa-8e07-ae4a39822592', '2025-02-23', FALSE, '天皇誕生日', 'LEGACY_HOLIDAYS_CSV'),
  ('c76f42ba-7514-554f-804f-be72df4ded9f', '2025-02-24', FALSE, '天皇誕生日 振替休日', 'LEGACY_HOLIDAYS_CSV'),
  ('0eabbfd7-ec30-53f6-baa9-06cd6afeba6d', '2025-03-20', FALSE, '春分の日', 'LEGACY_HOLIDAYS_CSV'),
  ('f398d47f-6542-54a5-b2a9-0967a0226df7', '2025-04-29', FALSE, '昭和の日', 'LEGACY_HOLIDAYS_CSV'),
  ('d2957892-d436-5b8f-9954-25a274f97d4b', '2025-05-03', FALSE, '憲法記念日', 'LEGACY_HOLIDAYS_CSV'),
  ('e2925a1c-ba50-53d4-b757-b865f28a4a45', '2025-05-04', FALSE, 'みどりの日', 'LEGACY_HOLIDAYS_CSV'),
  ('bf6d0904-942a-502a-8f2b-08bff2d2bb7a', '2025-05-05', FALSE, 'こどもの日', 'LEGACY_HOLIDAYS_CSV'),
  ('acd67ad8-1f23-531d-ab5b-a4a2f20f157d', '2025-05-06', FALSE, 'みどりの日 振替休日', 'LEGACY_HOLIDAYS_CSV'),
  ('2eac4897-1288-5d40-8acb-d34c3dfecee2', '2025-07-21', FALSE, '海の日', 'LEGACY_HOLIDAYS_CSV'),
  ('a5038704-7ff5-527c-87e2-a41280bf7fb1', '2025-08-11', FALSE, '山の日', 'LEGACY_HOLIDAYS_CSV'),
  ('95f05350-44d6-58f6-bcbf-7d4c83296a21', '2025-09-15', FALSE, '敬老の日', 'LEGACY_HOLIDAYS_CSV'),
  ('c5832cf6-ee61-5909-8f90-e016fc1376ed', '2025-09-23', FALSE, '秋分の日', 'LEGACY_HOLIDAYS_CSV'),
  ('7e3ddb6b-9af1-5cb9-af0b-f0110f2fd4bd', '2025-10-13', FALSE, 'スポーツの日', 'LEGACY_HOLIDAYS_CSV'),
  ('8fcc9633-4613-5def-86a8-cda3855470af', '2025-11-03', FALSE, '文化の日', 'LEGACY_HOLIDAYS_CSV'),
  ('b454867e-4f86-58c6-9600-1c75c24a3190', '2025-11-23', FALSE, '勤労感謝の日', 'LEGACY_HOLIDAYS_CSV'),
  ('fa59ec15-d81d-5e2b-9110-13d19906deec', '2025-11-24', FALSE, '勤労感謝の日 振替休日', 'LEGACY_HOLIDAYS_CSV'),
  ('e8bf9028-60f3-5073-bd37-0625f9fdd032', '2026-01-01', FALSE, '元日', 'LEGACY_HOLIDAYS_CSV'),
  ('d9d08d7e-7a93-504a-9195-55fa101a0dee', '2026-01-12', FALSE, '成人の日', 'LEGACY_HOLIDAYS_CSV'),
  ('b46c97b1-ed6b-5fed-a69b-68fc7a86ee80', '2026-02-11', FALSE, '建国記念の日', 'LEGACY_HOLIDAYS_CSV'),
  ('19985acc-d70d-5ae1-916e-36002e4a1849', '2026-02-23', FALSE, '天皇誕生日', 'LEGACY_HOLIDAYS_CSV'),
  ('7d1bb451-6b4c-5445-b9af-a8109c239071', '2026-03-20', FALSE, '春分の日', 'LEGACY_HOLIDAYS_CSV'),
  ('13a93694-9873-5f77-8764-1d1afefdb69b', '2026-04-29', FALSE, '昭和の日', 'LEGACY_HOLIDAYS_CSV'),
  ('659a030a-fffd-5a05-81c0-12668d9e618c', '2026-05-03', FALSE, '憲法記念日', 'LEGACY_HOLIDAYS_CSV'),
  ('5e1e0627-ff57-5684-8e78-d30cdca0f970', '2026-05-04', FALSE, 'みどりの日', 'LEGACY_HOLIDAYS_CSV'),
  ('789766fe-9658-5f6b-947e-5149ce42b781', '2026-05-05', FALSE, 'こどもの日', 'LEGACY_HOLIDAYS_CSV'),
  ('7295cf4c-eace-56f6-a3a2-b4419f237b48', '2026-05-06', FALSE, '憲法記念日 振替休日', 'LEGACY_HOLIDAYS_CSV'),
  ('06e563c8-4e74-5e9b-b9f6-cd5918e74080', '2026-07-20', FALSE, '海の日', 'LEGACY_HOLIDAYS_CSV'),
  ('b7a0b461-47dc-5fa7-a5ad-fa7c99422d77', '2026-08-11', FALSE, '山の日', 'LEGACY_HOLIDAYS_CSV'),
  ('9954fac8-3ee6-5fa0-8518-2e19eeb26591', '2026-09-21', FALSE, '敬老の日', 'LEGACY_HOLIDAYS_CSV'),
  ('f2f7a5ad-5a8f-54fd-a72a-66514c659b5f', '2026-09-22', FALSE, '国民の休日', 'LEGACY_HOLIDAYS_CSV'),
  ('dd56ed92-237a-5bef-8f48-41632a9438ff', '2026-09-23', FALSE, '秋分の日', 'LEGACY_HOLIDAYS_CSV'),
  ('1aa307b1-ab60-5b2e-91f8-421cc809b2e8', '2026-10-12', FALSE, 'スポーツの日', 'LEGACY_HOLIDAYS_CSV'),
  ('3b6ead61-c2bc-5ddd-936e-fe924e57161f', '2026-11-03', FALSE, '文化の日', 'LEGACY_HOLIDAYS_CSV'),
  ('4ea7fb83-349d-5264-98d0-0e8110513d97', '2026-11-23', FALSE, '勤労感謝の日', 'LEGACY_HOLIDAYS_CSV'),
  ('55cc81b0-29ec-558d-969b-8430c6b6e094', '2027-01-01', FALSE, '元日', 'LEGACY_HOLIDAYS_CSV'),
  ('9f02e336-5430-50b9-a987-ee8fcd1d6e64', '2027-01-11', FALSE, '成人の日', 'LEGACY_HOLIDAYS_CSV'),
  ('3c37f875-984a-5226-bbbe-c7155f2dfc0b', '2027-02-11', FALSE, '建国記念の日', 'LEGACY_HOLIDAYS_CSV'),
  ('ffbb072f-60ce-5b83-acf0-7d19dbbf158d', '2027-02-23', FALSE, '天皇誕生日', 'LEGACY_HOLIDAYS_CSV'),
  ('f7e48779-66fa-571a-976b-11d8c7cfa3cd', '2027-03-21', FALSE, '春分の日', 'LEGACY_HOLIDAYS_CSV'),
  ('6f91377c-11ae-56e4-be28-3c7035a499b8', '2027-03-22', FALSE, '春分の日 振替休日', 'LEGACY_HOLIDAYS_CSV'),
  ('ffe88378-b2ad-57a8-866d-2cb15409efa9', '2027-04-29', FALSE, '昭和の日', 'LEGACY_HOLIDAYS_CSV'),
  ('d9e239c5-8f49-55f9-996e-b724c2c45ede', '2027-05-03', FALSE, '憲法記念日', 'LEGACY_HOLIDAYS_CSV'),
  ('c76882a9-d3b0-5ea4-afc0-da460d0206da', '2027-05-04', FALSE, 'みどりの日', 'LEGACY_HOLIDAYS_CSV'),
  ('9329ab1f-d03d-526c-990f-d836fa8d2e4c', '2027-05-05', FALSE, 'こどもの日', 'LEGACY_HOLIDAYS_CSV'),
  ('9e53d03f-bb38-5f23-ba53-9acaeee77aef', '2027-07-19', FALSE, '海の日', 'LEGACY_HOLIDAYS_CSV'),
  ('38245029-2bc1-51d0-8d48-9dd1edfa2910', '2027-08-11', FALSE, '山の日', 'LEGACY_HOLIDAYS_CSV'),
  ('d6784045-29c3-5c53-be59-2de78322c842', '2027-09-20', FALSE, '敬老の日', 'LEGACY_HOLIDAYS_CSV'),
  ('066e7fa9-9800-5cb4-b291-c37928a4c111', '2027-09-23', FALSE, '秋分の日', 'LEGACY_HOLIDAYS_CSV'),
  ('82b6229f-83c5-53ed-a88e-ddb89f1dedaa', '2027-10-11', FALSE, 'スポーツの日', 'LEGACY_HOLIDAYS_CSV'),
  ('9a98e625-db9c-5f5e-868c-ed90e04bbf6c', '2027-11-03', FALSE, '文化の日', 'LEGACY_HOLIDAYS_CSV'),
  ('7ea4fc00-e5c6-5264-91c9-4f02b384e740', '2027-11-23', FALSE, '勤労感謝の日', 'LEGACY_HOLIDAYS_CSV')
)
INSERT INTO bills.b_business_calendar_days (
    calendar_day_id,
    calendar_id,
    calendar_date,
    business_day,
    holiday_name,
    source,
    created_at,
    updated_at
)
SELECT
    s.calendar_day_id,
    c.calendar_id,
    s.calendar_date,
    s.business_day,
    s.holiday_name,
    s.source,
    NOW(),
    NOW()
FROM seed_day s
CROSS JOIN calendar c
ON CONFLICT (calendar_id, calendar_date) DO UPDATE
SET business_day = EXCLUDED.business_day,
    holiday_name = EXCLUDED.holiday_name,
    source = EXCLUDED.source,
    updated_at = NOW();

-- Guardrail: all imported JP red days must be explicitly non-business days.
DO $$
DECLARE
  imported_count BIGINT;
  invalid_count BIGINT;
BEGIN
  SELECT COUNT(*)
    INTO imported_count
  FROM bills.b_business_calendar_days d
  JOIN bills.b_business_calendars c ON c.calendar_id = d.calendar_id
  WHERE c.calendar_code = 'JP'
    AND d.source = 'LEGACY_HOLIDAYS_CSV';

  SELECT COUNT(*)
    INTO invalid_count
  FROM bills.b_business_calendar_days d
  JOIN bills.b_business_calendars c ON c.calendar_id = d.calendar_id
  WHERE c.calendar_code = 'JP'
    AND d.source = 'LEGACY_HOLIDAYS_CSV'
    AND d.business_day IS DISTINCT FROM FALSE;

  IF imported_count <> 108 OR invalid_count <> 0 THEN
    RAISE EXCEPTION
      'JP calendar seed verification failed: expected=108, actual=%, invalid_business_days=%',
      imported_count,
      invalid_count;
  END IF;
END
$$;

COMMIT;
