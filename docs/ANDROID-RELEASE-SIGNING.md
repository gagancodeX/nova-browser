# Stable Android release signing

Android requires every in-place APK update to be signed with the same certificate as the installed app.

## One-time setup

1. Download the private signing bundle supplied separately to the repository owner.
2. Keep `browser-release.jks` in a secure backup. Never add it to Git.
3. Open repository Settings → Secrets and variables → Actions:
   https://github.com/gagancodeX/nova-browser/settings/secrets/actions
4. Add these repository secrets:
   - `NOVA_KEYSTORE_BASE64`: the full single-line contents of `browser-release.base64`
   - `NOVA_KEYSTORE_PASSWORD`: the value in `GITHUB-ACTIONS-SECRETS.txt`
   - `NOVA_KEY_ALIAS`: the alias in `GITHUB-ACTIONS-SECRETS.txt`
   - `NOVA_KEY_PASSWORD`: the value in `GITHUB-ACTIONS-SECRETS.txt`
5. Run the Android TV Browser APK workflow manually, or push a new Android source change.
6. Confirm the workflow uploads both a debug artifact and a stable-signed release artifact. A GitHub Release is created only after the signed release build succeeds.

## First stable release migration

Earlier CI APKs were debug-signed by the runner. They do not share the new permanent release certificate. The first APK signed with this key therefore requires uninstalling the previous app once, then installing the first stable-signed APK. Uninstalling may remove local bookmarks, history, channels and preferences.

After that migration, retain the same keystore permanently. Every future release signed by this key can be installed over the existing app, preserving application data.

## Security

- Never commit a `.jks`, `.keystore`, `.base64` file or passwords to the public repo.
- Never rotate or replace the signing key after publishing a stable release.
- Back up the JKS and passwords securely. If the keystore is lost, Android will reject future updates signed with a different key.
- The workflow compiles and uploads a debug APK even when secrets are absent, but it does not publish a release in that case.
