# Privacy Policy for Salut

**Last updated:** November 13, 2026

This Privacy Policy applies to **Salut**, an open-source YouTube client for
Android phones (package ID `io.github.aleixrodriala.arc.debug`). Salut is an
independent client based on NewTube and SmartTube. It is not affiliated with Google,
YouTube, or SmartTube's developer.

## 1. Summary

Salut has no analytics, trackers, advertising, or automatic crash reporting.
You can voluntarily send feedback to the project from the app.

## 2. Optional feedback

When you send feedback, Salut transmits your message and any selected images
over HTTPS to the feedback receiver. The report includes the app and Android
versions and a random report ID used for retries. It includes no account or
device identifier. Salut removes source image metadata before upload. Image
contents can still reveal personal details.

Drafts stay in app memory for the current session. The receiver stores accepted
reports with their attachments for delivery to the project via Telegram.

## 3. YouTube / Google

- **Sign-in is optional.** You can use Salut fully signed out. If you choose to
  sign in, Salut uses Google's official OAuth 2.0 **device-code flow** , you
  authorize the app on Google's own page; your password is never seen by, or
  shared with, the developer.
- **Tokens stay on your device.** Authentication tokens are stored in the
  app's local storage on your device. They are never transmitted to, or stored
  by, the developer. Since 1.15.1, neither Android's own device backup and
  phone-to-phone transfer nor the app's own Backup & restore include them, so
  a new install restored or moved that way starts signed out. Backup files
  made by the app before 1.15.1 still contain them.
- **Direct connection.** Video streams, search, and account data are fetched
  directly from YouTube/Google servers to your device. Your use of YouTube's
  services through Salut is also subject to Google's and YouTube's own terms
  and privacy policies.

## 4. Community services (optional, per-feature)

When the corresponding feature is enabled, Salut sends **only the ID of the
video you are viewing** to these community-run services to retrieve crowd-sourced
data. These requests are read-only and contain no account tokens, names, emails,
or other personal identifiers:

- **SponsorBlock** (`sponsor.ajay.app`) , sponsor-segment timestamps.
- **DeArrow** (`sponsor.ajay.app`, `dearrow-thumb.ajay.app`) , de-clickbait
  titles and thumbnails.
- **Return YouTube Dislike** (`returnyoutubedislikeapi.com`) , estimated dislike
  counts. Off by default from version 1.10.3 (Settings → Player → Dislike counts).

Each of these can be turned off in Settings. They are operated by their
respective projects under their own privacy policies, not by Salut.

## 5. Update checks

Unless you installed Salut from a store that manages updates for you (such as
F-Droid or IzzyOnDroid), the app periodically checks Salut's **GitHub
Releases** for a newer version. This is a normal web request to `github.com`;
like any web request it exposes your IP address to GitHub, but it sends no
account data or personal identifiers, and no data goes to the developer. You can
avoid these checks by installing and updating through F-Droid/IzzyOnDroid.

## 6. No profiling, no advertising, no monetization

Salut does not profile you, does not use analytics or automated
decision-making about you, and shows no ads. Salut is a free, non-commercial
project: it does **not** solicit donations or payments and contains no in-app
purchases.

## 7. Your rights (GDPR / CCPA)

Feedback reports are stored without account or device identifiers. If you\ninclude personal information in a report, see the contact section below. Data\nheld by Google/YouTube, Telegram, or community services is governed by their\nrespective policies.

## 8. Contact

For questions about this policy or Salut's privacy design, please open an issue
on the project's GitHub repository.
