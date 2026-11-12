# Public test signing key

`test.keystore.b64` is an intentionally public Android test key, generated only for
`app.orchard.tv.topshelf`. Its store password and key password are `android`, and
the alias is `androiddebugkey`. It is not the upstream Aerial signing key.

The test workflow restores it to `local/topshelf-test.keystore`. Builds with
`-PtopShelfTest` explicitly select that key, independently of the runner's debug
key and any local production signing settings. CI verifies the actual APK
signature and certificate against this keystore before publishing.

For a local test build, first run:

```sh
mkdir -p local
base64 --decode .github/test-signing/test.keystore.b64 > local/topshelf-test.keystore
```

Early prereleases through test 11 used runner-generated debug certificates.
Updating those can fail with INSTALL_FAILED_UPDATE_INCOMPATIBLE; uninstall only
`app.orchard.tv.topshelf` first, accepting loss of its test-only preferences.
Never uninstall or change `app.orchard.tv` to resolve a test signing mismatch.
Never use this key for production packages. No production signing secrets are
required by the workflow.
