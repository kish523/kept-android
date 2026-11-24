# Changelog

## 0.2.2: smoother on every phone

**Faster**
- Kept now runs fully optimised, and Android prepares it ahead of time when it's installed, so
  screens open and scroll more smoothly, especially on budget phones.
- Home sorts your receipts in the background, so scrolling never waits for it.
- The download is smaller.

**Lighter effects where they cost too much**
- On phones with less memory or older Android, Kept automatically uses lighter effects: plain
  translucent bars instead of live blur, a still background glow, and simpler animations. Same
  design, much less work for the phone.
- Switch it yourself any time: **Settings › Appearance › Reduce visual effects**.

## 0.2.1: reminders, deleting and updates

**Reminders**
- Kept now reminds you: at 9:00, 7, 3 and 1 day before a return window closes, and 30 and 7 days
  before a warranty ends.
- Answer right from the notification: **Keeping it**, **Returned it**, or **Remind me tomorrow**.
- Tap a reminder to open the receipt.
- Reminders survive restarts, app updates and travel across time zones, and a daily check re-arms
  any that went missing.
- Kept asks to send notifications after you save your first receipt. Change it any time in Settings.

**Deleting receipts**
- Swipe a receipt left on Home to delete it, or use the bin on the receipt screen.
- Every delete has **Undo**. Nothing is erased until the message goes away.
- **Settings › Clean up finished receipts** deletes the ones with nothing left to track: returned,
  or every window closed. Receipts with an open return or warranty are never touched.

**Updates**
- Kept updates itself: once a day it checks for a new version and downloads it.
- Android 12 and later: it installs in the background, usually when you leave the app. The first
  time, Android asks you to allow it once.
- Android 8 to 11: the next time you open Kept, Android asks you to tap **Install**.
- After an update, this "What's new" screen shows what changed. Find it again in Settings.
- Turn automatic updates off, or check now, in **Settings › Updates**.
- Updates only ask GitHub for a newer version. Nothing about your receipts leaves your phone.

**Privacy**
- Kept no longer copies your receipts to Google Drive backup. Moving to a new phone with Android's
  phone-to-phone transfer still brings them along.
- Kept is now a signed release build, so its data can't be read out over a USB cable.
- Kept is now free and open source (Apache License 2.0), so anyone can check how it treats your data.

## 0.2.0: the redesign

**New look**
- Kept's own colours (warm paper, Kept green, coral for deadlines that are close) instead of the
  wallpaper colours, and the Plus Jakarta Sans font.
- New app icon: a receipt, checked off, with a reminder dot. Works with Android 13+ themed icons.
- Light, dark, or match the phone: Settings (top right on Home), or the moon/sun button.
- Frosted glass bars that blur what scrolls underneath (Android 12+; a plain tint on older phones).

**Home**
- "Next up" card with the most urgent deadline and a countdown ring.
- **Keeping it** / **Returned it** right on the card. Both can be undone from the message that follows.
- All / Returns / Warranties switch with counts, a clear (×) button in search, grouped rows with a
  category icon, a bar showing how much of the window has gone, and a days-left pill.

**Check receipt**
- The photo at the top, with a scan line while Kept reads it, and shimmering placeholders until it's done.
- Fields Kept wasn't sure about are amber, say why, and pulse once. "N to check" in the top bar jumps to them.
- Quick purchase dates: Today, Yesterday, 2 days ago, A week ago.
- Return and warranty chips now show which one is picked (they never did before).
- Each return window says where it came from: the receipt, the store's policy, or you.
- Shows exactly when you'll be reminded.
- Save shrinks into a spinner, then a check.

**Motion**
- Screens slide like iOS: the new one comes in from the right while the one below drifts left and dims.
  On Android 14+ the back gesture previews this as you swipe.
- Springy press feedback on cards, chips and buttons; lists rise in on first open.
- All of it follows the phone's *Remove animations* setting.

**Under the hood**
- Database v2 (adds "Keeping it" to items), with an automatic migration that keeps every receipt,
  and a test that runs it against the exported v1 schema.
- Re-saving an edited receipt keeps what you'd already decided (kept, returned).
- The document scanner starts when you tap Scan, not when Home opens.
- Test builds are now signed with one fixed key, so each new build installs over the last and keeps
  your receipts. **Coming from 0.1.0: uninstall it once first** (it was signed with a throwaway key).

Reminders still don't fire yet: that's the next release.

## 0.1.0

First test build: scan or share a receipt, on-device reading, review card, Home list.
