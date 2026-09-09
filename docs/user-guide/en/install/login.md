---
description: Sign-in page, UI language, forgot password, and profile
---

# Sign-in, language and password reset

## Sign-in

Open `http://<server-ip>/` (or `https://your-domain/` when HTTPS is on). It must match `PUBLIC_HOST` in `config.env`. A mismatch shows **cross-site authentication request was rejected** — see [Offline install · Which URL](offline.md#which-url-to-open-cloud-vms).

1. Enter **username or email** and password.
2. A super admin can change login copy, background and logo under **Platform Administration → Login branding**.
3. Username and email are unique and must not collide with each other (either can be used to sign in).

## Language

After login, pick **简体中文** or **English** in the header. The choice is stored in the browser.

## Forgot password

On the login page:

1. Enter username or email.
2. If the account exists and is active, a random 12-character password is emailed to **that user’s mailbox**.
3. The UI always says the mail was sent if the account exists, so accounts cannot be enumerated.

{% hint style="warning" %}
**Platform Administration → Mail settings** must have a working SMTP. If SMTP is not ready, the password is not changed.

If the super admin forgot the password and has no mailbox, run this in the install directory:

```bash
sudo ./reset_admin_en.sh
```
{% endhint %}

Set a reachable email in **Account** first.

## Sign out

Header avatar → **Sign out**.

## Field app

The app uses the same `/api/v1`. Send `"client": "mobile"` on login. See [App API](../appendix/app-api.md). Do not send that field from the web UI.
