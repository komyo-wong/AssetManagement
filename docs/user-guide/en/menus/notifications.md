---
description: Alert Center → My notifications
---

# My notifications

Path: **Alert Center → My notifications**.

## Channels

- **Email**: A super admin must enable SMTP under **Platform Administration → Mail settings**. Use the account email or another address.
- **Webhook**: POST JSON to a URL.

## Subscriptions

On alert / on recovery, minimum severity, interval:

- Once: one push on open and one on recovery
- Or every 5 / 15 / 60 minutes while still open

Advanced: quiet hours, still notify CRITICAL, filter rule types. Delivery log: sent / failed / pending / skipped (often debounce).
