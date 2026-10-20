# Portfolio demonstration catalogue

Prepared catalogue, not yet created in the hosted database. All venues and events below are fictional. Payments are simulated.

## Administrator setup

Register the owner's account through the hosted frontend. From a trusted PostgreSQL connection, look up the normalized email in `account_email`, verify the matching account UUID and display name, and promote only that UUID in `app_user`. The account must have a password hash; legacy seeded accounts are not login accounts. Sign out and back in to refresh the session's role, then open `/admin/catalog`.

Do not grant administrator privileges through public registration, put a password in a migration, or promote all accounts. Database credentials remain in private provider settings. The actual owner account has not yet been identified or promoted.

## Demo content

Use timezone `Asia/Kolkata` for all venues. Mark addresses as fictional demo locations rather than implying actual bookable venues.

| Event | Venue and city | Category | Layout | Section prices | Booking fee | Duration |
| --- | --- | --- | --- | --- | --- | --- |
| Afterglow: An Acoustic Evening | Riverside Hall, Mumbai | Music | Premium P1–P24; Standard S1–S48 | Premium ₹899; Standard ₹499 | ₹25 | 120 minutes |
| The Friday Laugh Club | Spotlight Studio, Bengaluru | Comedy | Front F1–F20; Standard S1–S40 | Front ₹599; Standard ₹349 | ₹20 | 90 minutes |
| The Last Letter | Lantern Theatre, Pune | Theatre | Dress Circle D1–D24; Stalls S1–S48 | Dress Circle ₹749; Stalls ₹449 | ₹25 | 110 minutes |

Descriptions:

- **Afterglow:** Settle in for an intimate evening of acoustic arrangements, warm harmonies and original songs. This fictional event demonstrates SeatLock's concert-booking experience; no real admission is sold.
- **The Friday Laugh Club:** A relaxed evening of observational comedy and everyday stories, presented in a small seated studio. This fictional event is part of a portfolio demonstration; payments are simulated.
- **The Last Letter:** A fictional stage drama about an unexpected letter and the conversations it starts. Explore reserved seating and transparent checkout pricing in this portfolio demonstration.

## Publication sequence

1. Create the three venue layouts through the administrator catalogue.
2. Create drafts with event dates 7, 10 and 14 days after the actual setup date. Start sales immediately and close sales one hour before each event. Use the timezone displayed by the editor when entering dates.
3. Save descriptions, category and duration. Leave poster URLs empty until suitable images are available; do not use broken placeholder URLs.
4. Preview each draft and verify its seat counts and all section prices, then publish.
5. Verify public category/city filters and complete a demo booking on one event.
6. Close sales for the seeded Test Concert after the replacements work. Retain its existing test booking and receipt; do not delete the event or booking records.

The catalogue and administrator setup remain pending until the owner's account is identified and a trusted database session is available.
