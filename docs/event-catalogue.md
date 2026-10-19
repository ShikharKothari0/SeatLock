# Event content, draft editing and discovery

Implemented 19 October 2026. This extends the existing venue/event workflow; it does not add a separate organiser portal.

## Administrator workflow

Create a venue and draft event in Events and venues, then choose **Edit and preview**. Edit the event name, description, category, optional poster URL/duration, schedule, section prices and booking fee. Save the draft to update its preview. Return to Events and venues to publish it.

The preview shows saved data, not unsaved form values. Draft previews require an administrator account and stay unavailable through public event endpoints. Preview pages have no customer booking action. The venue and copied seating layout cannot be changed by this editor.

Only DRAFT events can be edited. A published or closed event is read-only, including its content, schedule and prices. Existing reservations and receipts are therefore unaffected. Every save records an audit entry and increments a revision. A stale save receives a conflict instead of silently replacing another administrator's changes; reload the saved event before trying again. Publication and editing lock the same event row.

## Event content and posters

Flyway V9 adds description, category, poster URL, optional duration and revision. Existing events retain their inventory and receive empty content and the Other category; their duration is not invented. Categories are Music, Comedy, Theatre, Sports, Workshop and Other.

Descriptions are plain text, limited to 5,000 characters. Posters are optional external HTTPS image URLs without embedded credentials, limited to 2,048 characters. The backend stores the URL and does not fetch the image. Browsers load images without a referrer and show a placeholder when a poster is missing or fails. Administrators should use an image host they trust; uploads, image processing and object storage are not included.

## Customer discovery

The home page combines event/venue-name search, city, category and inclusive from/to dates. Filters are stored in the URL and survive refresh. Dates use the browser's local timezone, stated in the form. Clear filters restores the default view. Results are ordered by event start time.

Events whose start time has passed are hidden by default. Include past events makes them discoverable without deleting them or affecting booking history. Future events with closed sales remain visible with a closed-sales label. Past events, closed sales and future sales openings do not offer new seat selection; the backend still enforces the actual sale window independently.

Filtering currently runs on the public event list in the browser, suitable for the small personal-project catalogue. Server-side pagination/search, recurrence, event cancellation/refunds, editing published events and a venue-layout editor remain outside this milestone.

## API

- `GET /api/admin/catalog/events/{id}` returns the saved event, prices and revision for admin preview.
- `PUT /api/admin/catalog/events/{id}` validates and saves a complete draft update with its expected revision. Administrator authentication and CSRF protection are required.
- Public event/receipt responses now also include description, category, posterUrl, durationMinutes, publication and saleEndTime. Existing response fields remain unchanged.

## Validation

Focused backend tests cover private preview/publication, stale edits, published-event protection, exact edited prices, poster validation, administrator access and CSRF. Existing catalogue and payment regressions run alongside them. Browser coverage exercises combined discovery filters, reload, past/closed labels and mobile layout. The live catalogue script exercises editing and saved preview before publication, filtered discovery and the priced booking flow.

Verified locally on 19 October 2026: 21 backend integration tests (EventEditorialTest, CatalogPricingTest and DemoPaymentTest), 17 frontend unit tests, 13 controlled browser scenarios and 5 live catalogue checks passed. Frontend build and lint passed. The mobile discovery screenshot was visually reviewed, and the browser check verified no horizontal overflow. Flyway V9 is applied locally and backend health reports UP. Controlled browser tests use API fixtures; the live catalogue checks use the running services. These results do not establish deployment or production-load readiness.

Subsequent milestone: [PostgreSQL persistence and backup/restore](database-recovery.md) is verified locally. Deployment packaging is now locally verified; next are CI/release checks and real-host validation.
