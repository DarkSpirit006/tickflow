# Contributing

Thanks for contributing to TickFlow.

## Development

Use the included Gradle wrapper. Run:

```bash
./gradlew clean build
```

Keep the main implementation independent of Paper-only APIs whenever possible. Version-specific behavior should fail closed: an unavailable optional hook must not prevent the plugin from loading.

## Pull requests

Keep changes focused, explain compatibility implications, and include reproduction steps for bug fixes.
