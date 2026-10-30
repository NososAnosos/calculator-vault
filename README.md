# Calculator Vault

A working Android calculator with a hidden, encrypted notepad behind it.

Type your PIN as if it were a calculation and press `=`. Instead of a result,
your private notes open. Anyone else just sees a calculator that calculates.

This is **v1**: a small, early release, shared while it's being built in public.
It is not on the Play Store yet. You install it by sideloading the APK.

> Not affiliated with or endorsed by Google. The calculator's look is modeled on a
> stock Android calculator so the app blends in, but no code, icons, fonts, or
> assets from any other app are included.

## What v1 does

**The calculator**
- A real arithmetic engine (BigDecimal, 50-digit working precision) with correct
  precedence, parentheses, percent, and implicit multiplication (`2(3)`, `2π`).
- A scientific pad: `√ x² π e ^ ! sin cos tan` and their inverses, `ln log eˣ 10ˣ`,
  and Deg/Rad.
- Live preview while you type, history that persists across restarts, and
  Light / Dark / System themes.
- Uses your device's system font when available. No fonts are bundled.

**The vault**
- One vault, opened with a 4–12 digit PIN.
- Encrypted notes: create, edit, and delete. They autosave.
- Notes stay on the device. The only permission is vibration (key haptics).
  There's no internet permission, no accounts, no analytics, and no notifications.

## First-run setup: setting your PIN

1. Open the app. It's labeled **Calculator**.
2. Make sure the display is empty, then **long-press `=`**.
3. Enter the PIN you want (4–12 digits) twice.

This setup gesture only works while no vault exists. Once you have set a PIN,
long-pressing `=` does nothing, so the gesture doesn't give the vault away. In
v1 it's a placeholder and will probably change in a later version.

To open the vault later, type your PIN on the keypad and press `=`.

There's no way to change or recover the PIN in v1. **If you forget it, your
notes are gone.** That's deliberate: nothing else can unlock them.

## Install (sideload)

Requires Android 12L (API 32) or newer.

1. Download `calculator-vault-v1.0.apk` from the
   [Releases page](../../releases) (or from kbird.ai).
2. Verify it (see below). This step is optional, but recommended.
3. Open the APK on your phone. Android will ask you to allow installs from that
   source (your browser or file manager). Allow it, then tap **Install**.
   - Play Protect may warn you about an app it doesn't recognize. That's expected
     for an app that isn't on the Play Store.
4. If you want, turn "Install unknown apps" back off for that source afterwards.

Or with `adb`: `adb install calculator-vault-v1.0.apk`

## Verify the download

Every release lists the APK's SHA-256 hash. All releases are signed with the
same key, and Android only installs updates signed with that key.

**v1.0**
- APK SHA-256: `a11aa0d91680809b0048c6514e913ba97ce2234084d3598227104b83a055dc14`
- Signing certificate SHA-256:
  `d0:73:60:89:91:56:ef:8b:dd:78:13:35:01:8b:f7:1b:c9:ef:22:2f:50:97:06:80:15:41:c2:77:46:a3:c4:38`

Check the file hash:

```sh
sha256sum calculator-vault-v1.0.apk           # Linux
shasum -a 256 calculator-vault-v1.0.apk       # macOS
certutil -hashfile calculator-vault-v1.0.apk SHA256   # Windows
```

Check the signing certificate (requires the Android SDK build-tools):

```sh
apksigner verify --print-certs calculator-vault-v1.0.apk
# Signer #1 certificate SHA-256 digest: d07360899156ef8bdd781335018bf71bc9ef222f509706801541c27746a3c438
```

You can also check the certificate on the phone with an app such as
[AppVerifier](https://github.com/soupslurpr/AppVerifier).

## Build from source

You need JDK 17 or newer and the Android SDK (compileSdk 36).

```sh
git clone https://github.com/NososAnosos/calculator-vault.git
cd calculator-vault
echo "sdk.dir=/path/to/Android/Sdk" > local.properties   # or set ANDROID_HOME
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew :app:assembleDebug          # debug APK in app/build/outputs/apk/debug/
```

`./gradlew :app:assembleRelease` builds a minified release APK. Without signing
variables it comes out unsigned. To sign it with your own key, set
`CV_KEYSTORE_FILE`, `CV_KEYSTORE_PASSWORD`, `CV_KEY_ALIAS`, and
`CV_KEY_PASSWORD` in the environment. A build from source is signed with a
different key than the official releases, so Android won't install it over an
official install, or the other way around. Uninstall first; that deletes the
vault.

## Security notes and limits

How it works:
- **Key derivation:** the PIN goes through PBKDF2-HMAC-SHA256 with 600,000
  iterations and a random 16-byte per-install salt, producing a 256-bit key.
- **Encryption:** each note is sealed with AES-256-GCM, with a fresh nonce on
  every write. The note's id is bound in as associated data, so ciphertexts can't
  be swapped between notes.
- **No PIN hash:** a wrong PIN simply fails to decrypt a sealed check value.
- **What's on disk:** the database holds only random ids and ciphertext, with
  SQLite `secure_delete` on. Storage names don't mention a vault, notes, or a PIN.
- **No backups:** app backup is disabled, and the data is excluded from cloud
  backup and device-to-device transfer.
- **Screen privacy:** vault screens set `FLAG_SECURE`, so they don't appear in
  screenshots or the recents thumbnail.
- **Keyboard:** vault text fields ask the keyboard not to learn what you type.
- **Locking:** the vault locks as soon as the app leaves the screen, and the key
  and decrypted notes are wiped from memory.
- **Wrong PINs:** the first 5 failed PIN-shaped entries are free. After that,
  lockouts start at 30 s and double each time, up to 24 h. During a lockout
  candidate PINs aren't checked at all.

Limits to know:
- **A short PIN can be brute-forced offline.** PBKDF2 slows that down but can't
  stop it if someone copies the app's private data, for example from a rooted or
  forensically imaged phone. Use a longer PIN for stronger protection.
- **The lockout uses the wall clock,** so changing the device clock can shorten it.
- **The PIN sits briefly in memory** as an immutable string during the check.
- **It hides the vault, not the app.** Anyone who looks closely can see that a
  "Calculator" app from an unknown source is installed. It's camouflage, not
  deniability against a determined, technical adversary.
- **This is early software** and hasn't had an independent security audit.
  Don't rely on it for anything life-critical.

Found a security problem? Email hello@kbird.ai rather than opening a public issue.

## What's next

v1 is the foundation. Bigger things are in progress, including more than one
vault on the same calculator and storing more than notes. Follow along at
[kbird.ai](https://kbird.ai) or [@kbird_ai](https://x.com/kbird_ai). There are no
dates yet.

## License

GPL-3.0. See [LICENSE](LICENSE).
