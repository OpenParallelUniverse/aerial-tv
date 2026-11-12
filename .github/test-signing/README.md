# Public test signing key

`test.keystore.b64` is an intentionally public Android test key, generated only for
`app.orchard.tv.topshelf`. Its store password and key password are `android`, and
the alias is `androiddebugkey`. It is not the upstream Aerial signing key.

The test workflow restores it to the runner's debug-keystore location so release
builds use a stable certificate and successive test APKs can update each other.
Never use this key for production packages. No production signing secrets are
required by the workflow.
