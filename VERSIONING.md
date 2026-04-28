# Versioning

This project uses git tag-driven versioning. The `versionName` and `versionCode` in the APK are derived automatically from git at build time — do not edit them manually in `app/build.gradle`.

## How it works

| Field | Source | Example |
|---|---|---|
| `versionName` | Latest git tag (via `git describe`) | `1.1.0` |
| `versionCode` | Total git commit count | `1681` |

On an untagged commit (e.g. mid-development on `stag`), `versionName` will include commits since the last tag and the short commit hash: `1.0.0-8-gabcd123`. This is expected and useful for identifying staging builds.

## Branch strategy

| Branch | `versionName` | Purpose |
|---|---|---|
| `stag` (between releases) | `1.0.0-8-gabcd123` | Testing / staging builds |
| `main` (tagged) | `1.1.0` | Production release builds |

Tags live on `main` and represent production releases. Do not tag `stag`.

## Releasing a new version

1. Develop and test on `stag`
2. Merge `stag` into `main`
3. Tag the merge commit on `main` using [semantic versioning](https://semver.org) with a `v` prefix:
   ```bash
   git checkout main
   git pull origin main
   git tag v1.1.0
   git push origin v1.1.0
   ```
4. Build the release APK from `main`:
   ```bash
   ./gradlew assembleRelease
   ```
   The APK will have `versionName = "1.1.0"`.

## Versioning convention (SemVer)

Given a version `MAJOR.MINOR.PATCH`:

- **MAJOR** — breaking changes or significant new capabilities
- **MINOR** — new features, backwards compatible
- **PATCH** — bug fixes and minor improvements

## Checking the current version

```bash
git describe --tags --always
```
