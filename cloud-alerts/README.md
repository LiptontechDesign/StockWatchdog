# StockWatchdog Free Cloud Alerts

This folder is the no-paid-Firebase backend for closed-app alerts.

It uses:

- Firebase Cloud Messaging for push delivery.
- Firestore Spark/free tier for synced alert rules.
- GitHub Actions scheduled runs as the free alert checker.

It does not use Firebase Functions, Cloud Scheduler, Cloud Run, Cloud Build, or Blaze-only services.

Required Firebase console setting:

1. Firebase Console > Authentication > Get started.
2. Sign-in method > Anonymous > Enable.

GitHub secret required:

- `FIREBASE_SERVICE_ACCOUNT_JSON`

The runner reads `alertUsers` from Firestore, checks tracked symbols, records alert events, and sends FCM data messages to the synced phone token.
