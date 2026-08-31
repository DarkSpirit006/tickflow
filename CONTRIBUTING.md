# Contributing

Thanks for contributing to TickFlow. Keep changes focused, testable, and easy to review.

## Development

TickFlow targets Purpur 26.2 and Java 25. Use the included Gradle wrapper:

```bash
./gradlew clean build
```

Test changes on a disposable server. Timing changes should be tested both at 20 TPS and under sustained CPU pressure. When fixing a timing bug, attach diagnostic CSV data to the pull request when practical.

## Code style

Use four spaces, UTF-8, LF line endings, descriptive names, and small classes with one clear responsibility. Avoid broad exception handling when a feature-specific failure can be isolated.

## Pull requests

Explain the behavior change, compatibility impact, and test procedure. Avoid drive-by formatting changes unrelated to the patch.
