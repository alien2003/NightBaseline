# Security policy

NightBaseline reads sleep and related health data from Health Connect and processes it on the phone. This file says
what the app is designed not to do, how to report a vulnerability privately, and how release APKs are signed. What the
app reads and stores is described in [docs/privacy.md](docs/privacy.md).

## Supported versions

Fixes are made on `main` and published in a new release. Only the latest release is supported; update to it before
reporting.

## Reporting a vulnerability

**Do not open a public issue, pull request or discussion for a vulnerability.**

Report it privately through GitHub: the repository's **Security** tab → **Report a vulnerability**, or
<https://github.com/alien2003/NightBaseline/security/advisories/new>. Describe the problem, the app version, the device
and Android version, and the steps to reproduce. Do not include real health data.

If there is no "Report a vulnerability" button, open an issue titled "Security contact request" without any details,
and you will be contacted for a private channel.

## Scope

In scope, for example: any way the app could send data off the phone, write or change health data, expose its stored
nights to other apps, or be made to act through its exported components; problems in the build and release pipeline
that could change a signed APK.

Out of scope: Health Connect itself, the source apps, Android, and devices that are rooted or otherwise compromised.

## Design

- **No network access.** The manifest removes `INTERNET` and `ACCESS_NETWORK_STATE`. `MergedManifestTest`, the CI
  check of the merged release manifest and the release workflow (on the signed APK, with `aapt2 dump permissions`)
  fail if either appears.
- **Read-only.** Only Health Connect READ permissions are declared.
- **Private storage only**, excluded from backups (`allowBackup="false"`). No content provider and no browsable deep
  links are exported; notification links are explicit intents to the app's own activity.
- **No health data in the repository, CI or release notes.** Tests and screenshots use synthetic data; developer
  exports stay in the git-ignored `.data/` ([docs/DATASET.md](docs/DATASET.md)).

## Signing key policy

- **One release key.** Every `NightBaseline-<version>.apk` is signed with the same key, so later releases install over
  earlier ones. Its certificate SHA-256 is
  `26:EC:1D:EC:35:93:AD:CE:D2:68:BB:64:1E:CB:74:7B:2E:8B:15:42:D5:66:24:78:20:2F:E0:C6:B1:F4:D3:77`.
- **Where it lives.** Outside the repository, and for the build only in the repository secrets
  `NIGHTBASELINE_KEYSTORE_BASE64`, `NIGHTBASELINE_KEYSTORE_PASSWORD`, `NIGHTBASELINE_KEY_ALIAS` and
  `NIGHTBASELINE_KEY_PASSWORD` ([docs/development.md](docs/development.md#signing-secrets)). It is never committed.
- **When it is used.** Only by the build workflow: for a `v*` tag (published), and for check builds on `main` and dry
  runs (verified, then discarded). The decoded keystore is deleted at the end of every run, also after a failure.
  Release-signed APKs are never kept as Actions artifacts beyond the one-day hand-over to the publishing job.
- **If the key is ever lost or compromised**, a new key will be announced in a security advisory and in the notes of
  the first release signed with it. Android refuses updates signed with another key, so users would have to uninstall,
  install the new build and grant the Health Connect permissions again.

## Verifying a download

```sh
sha256sum -c SHA256SUMS.txt
apksigner verify --print-certs NightBaseline-<version>.apk
```

`sha256sum -c` detects a damaged download. The stronger check is the certificate: the `SHA-256 digest` printed by
`apksigner` (Android SDK build-tools) must be `26ec1dec3593adced268bb641ecb747b2e8b1542d5662478202fe0c6b1f4d377`, the
value above without colons, and the same for every release. The release notes print it as well.

## Build and release pipeline

- Every GitHub Action is pinned to a full commit SHA. Dependabot proposes pin and Gradle dependency updates at most
  once a month, for releases at least 7 days old.
- Workflows default to read-only tokens; only the job that publishes a release gets `contents: write` and it holds no
  key. No checkout keeps the token (`persist-credentials: false`).
- Signed builds use no Gradle cache, build cache or configuration cache, so nothing from another run can end up in a
  signed APK.
- The signed APK is checked before publishing: valid signature, not a debug key, the expected versionName and
  versionCode, no network permission.
