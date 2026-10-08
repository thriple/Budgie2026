# Budgie

A bird-themed budget tracker for Android with a 4x3 home-screen widget.

- Type a budget in whole dollars; starting a new one archives the old one (leftovers do not carry over).
- Subtract amounts with the number pad, in the app or straight from the widget.
- Every entry stores its amount, date and time, and feeds the graphs.
- The archive lists past budgets with graphs, sortable by newest/oldest and lowest/highest.

## Getting the app

Every push to `main` is built by GitHub Actions (`.github/workflows/build.yml`) and published on the
repo's **Releases** page. Open Releases on the phone, download the newest `budgie-N.apk`, tap it, and
allow "install unknown apps" when asked. New builds install over old ones and keep your data.

## Layout

- `app/src/main/java/com/budgie/data` — storage (SharedPreferences + JSON)
- `app/src/main/java/com/budgie/ui` — the app screens (Jetpack Compose) and the soft dark theme
- `app/src/main/java/com/budgie/widget` — the home-screen widget (Jetpack Glance)

`debug.keystore` is a throwaway signing key kept in the repo on purpose so every build is signed alike.
