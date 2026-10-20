# Event content, draft editing and discovery

Implemented 19 October 2026. This extends the existing venue/event workflow; it does not add a separate organiser portal.

## Administrator workflow

Create a venue and draft event in Events and venues, then choose **Edit and preview**. Edit the event name, description, category, optional poster URL/duration, schedule, section prices and booking fee. Save the draft to update its preview. Return to Events and venues to publish it.

The preview shows saved data, not unsaved form values. Draft previews require an administrator account and stay unavailable through public event endpoints. Preview pages have no customer booking action. The venue and copied seating layout cannot be changed by this editor.

Drafts support full editorial and pricing changes. Published, closed and archived events allow description, category and poster edits only; their name, duration, schedule and prices remain locked. Existing reservations and receipts are unaffected. Every save records an audit entry and increments a revision. A stale save receives a conflict instead of silently replacing another administrator's changes; reload the saved event before trying again. Publication and editing lock the same event row.

## Event content and posters

Flyway V9 adds description, category, poster URL, optional duration and revision. Existing events retain their inventory and receive empty content and the Other category; their duration is not invented. Categories are Music, Comedy, Theatre, Sports, Workshop and Other.

Descriptions are plain text, limited to 5,000 characters. Posters are optional external HTTPS image URLs without embedded credentials, limited to 2,048 characters. The backend stores the URL and does not fetch the image. Browsers load images without a referrer and show a placeholder when a poster is missing or fails. Administrators should use an image host they trust; uploads, image processing and object storage are not included.

## Customer discovery

The home page combines event/venue-name search, city, category and inclusive from/to dates. Filters are stored in the URL and survive refresh. Dates use the browser's local timezone, stated in the form. Clear filters restores the default view. Results are ordered by event start time.

Events whose start time has passed are hidden by default. Include past events makes them discoverable without deleting them or affecting booking history. Future events with closed sales remain visible with a closed-sales label. Past events, closed sales and future sales openings do not offer new seat selection; the backend still enforces the actual sale window independently.

Filtering currently runs on the public event list in the browser, suitable for the small personal-project catalogue. Server-side pagination/search, recurrence, event cancellation/refunds, published schedule/price changes and a venue-layout editor remain outside this milestone.

## API

- `GET /api/admin/catalog/events/{id}` returns the saved event, prices and revision for admin preview.
- `PUT /api/admin/catalog/events/{id}` validates and saves a complete draft update with its expected revision. Administrator authentication and CSRF protection are required.
- Public event/receipt responses now also include description, category, posterUrl, durationMinutes, publication and saleEndTime. Existing response fields remain unchanged.

## Validation

Focused backend tests cover private preview/publication, stale edits, published-event protection, exact edited prices, poster validation, administrator access and CSRF. Existing catalogue and payment regressions run alongside them. Browser coverage exercises combined discovery filters, reload, past/closed labels and mobile layout. The live catalogue script exercises editing and saved preview before publication, filtered discovery and the priced booking flow.

Verified locally on 19 October 2026: 21 backend integration tests (EventEditorialTest, CatalogPricingTest and DemoPaymentTest), 17 frontend unit tests, 13 controlled browser scenarios and 5 live catalogue checks passed. Frontend build and lint passed. The mobile discovery screenshot was visually reviewed, and the browser check verified no horizontal overflow. Flyway V9 is applied locally and backend health reports UP. Controlled browser tests use API fixtures; the live catalogue checks use the running services. These results do not establish deployment or production-load readiness.

Subsequent milestone: [PostgreSQL persistence and backup/restore](database-recovery.md) is verified locally. Deployment packaging is now locally verified; next are CI/release checks and real-host validation.


## Published details, archive and draft deletion (20 October 2026)

Administrators can use **Edit details and preview** to update descriptions, categories and HTTPS poster URLs after publication, including closed or archived events. Titles, duration, venue, schedule, prices and seats remain locked. Revision checks reject stale saves. The content endpoint accepts only the permitted fields and rejects additional fields sent by a client.

**Archive event** closes sales and hides the event from public lists, details, seat lists and price lookup. Booking receipts and history remain accessible to their owners. Archive is not a return to draft; archived events cannot reopen. Existing pending reservations cannot complete after sales close, but can be released or expire normally.

**Reopen sales** restores a manually closed, non-archived event to published status when the event and its sales closing time are still in the future. It preserves the original sales opening time, all seats, bookings and prices. The administrator-only, CSRF-protected `POST /api/admin/catalog/events/{id}/reopen` endpoint checks eligibility under a database row lock, increments the revision and records a `REOPEN` audit entry. It does not extend the sales window or restore expired reservations.

During venue creation, **Delete section** removes an unsaved layout section; at least one section is required. Successful venue creation clears its fields and resets the layout to the Standard section while selecting the new venue for draft creation. Successful event creation clears its form and venue selection. Failed submissions keep the entered values. Timezone and booking-fee defaults are retained.

**Delete draft** removes an unpublished event, its section prices and generated seats. It retains the reusable venue layout and audit trail. Published/closed/archived events and drafts containing reservation records cannot be deleted. Both actions ask for confirmation in the interface and require administrator authentication and CSRF protection.

Migration V10 adds the archive flag, defaulting to false, with a database constraint requiring archived events to have closed sales. No existing event is archived or deleted by migration.

Validation for this update: nine EventEditorialTest integration tests passed against isolated PostgreSQL/Redis containers; all seven live catalogue/browser scenarios passed against the local Docker deployment. Frontend build, lint and 17 unit tests passed, and both Docker images built successfully. Hosted deployment of this update must still complete before these controls are available on the public site.
