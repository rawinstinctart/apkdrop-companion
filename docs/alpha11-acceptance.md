# Alpha 11 — encrypted offline device transfer

Version: `0.1.0-alpha.11-preview`, versionCode 14. Preview uses the original Alpha signer and existing package for in-place upgrades.

## Implemented

Settings → Gerätewechsel contains two actions: export encrypted backup through Android's Storage Access Framework, and open an existing backup. No account, server, Google Drive integration, analytics, or automatic upload.

- Only the explicitly saved app identity pins (slug/package/signers/name) and followed developer IDs/profiles are backed up. No APKs, installed-app inventory, tokens, installer files or device fingerprint.
- The file is JSON with authenticated AES-256-GCM encryption, random 16-byte salt, 12-byte nonce, PBKDF2-HMAC-SHA256 with 210,000 iterations and exact schema/AAD. Password is never persisted; minimum 10 characters.
- A wrong password or modified ciphertext fails closed. File and decoded data are size-bounded.
- Import previews the number of apps/developers and requires separate user confirmation before writing.
- Merging cannot silently overwrite an existing pinned package/signing identity or existing developer follow. Data shapes and max capacities use existing local validation. An import conflict blocks the merge.
- Following and app state remain local and no installation is triggered by import. Any subsequent update must fetch fresh contract facts and pass the original local hash/signature/identity gates.
- DropPilot and metadata job scheduling reconcile after a successful import.

## Verification

Python preflight tests, native debug/preview tests, lint, CI unsigned manifest profile 14; external original-key signing; signer/hash release preflight; **real Android file-picker round-trip on device still OPEN**. Interrupted import/export, rotation, device-to-device AES-GCM compatibility and OEM provider behavior must be checked on the physical device.

## Deferred product gates

Companion one-tap self-update and verified production App Links are not enabled by this source change. Both need an authorized immutable APKDrop install contract for the Companion, production signing configuration, and physical installer validation; do not bypass the APKDrop-only installation network boundary by trusting arbitrary GitHub binary links.

No Cloudflare, FREY, HIOS production or release signing secrets changed.
