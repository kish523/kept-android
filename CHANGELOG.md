# Changelog

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

Reminders still don't fire yet: that's the next release.

## 0.1.0

First test build: scan or share a receipt, on-device reading, review card, Home list.
