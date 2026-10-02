# Panacea

A private, offline medication tracker for Android.

Add the medications you take, then log a dose with one tap. Panacea shows when you last took each one,
how much you've had in the last 24 hours, and reminds you when the next dose is due.

- **Reminders**: daily, weekly, monthly or every few hours, with snooze and mute. A dose taken a little
  early counts.
- **Learn My Routine**: Panacea learns when you usually take a medication and asks if a usual dose
  isn't logged.
- **Pill photos**: a front and back photo of each pill, so you can check one at a glance.
- **History and totals** for the last day, week, month and all time, with pill weights.
- **Backup, restore and CSV export.** Restore also reads backups from Panacea 3.
- Seven themes, custom sounds, and TalkBack support.

## Privacy

Everything stays on your phone. Panacea has no network permission, no account and no analytics. Files
leave the phone only when you back up or export them.

## Building

Needs JDK 17 or 21 and the Android SDK (compile SDK 36). Runs on Android 13 and later.

```bash
./gradlew assembleRelease
```

## License

MIT. See [LICENSE](LICENSE).
