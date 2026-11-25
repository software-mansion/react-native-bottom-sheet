# Testing

## Android JVM tests

Install JDK 21 and make it available to Gradle. Robolectric needs Java 21 to run
tests that target Android API 36.

Prepare the native example project and run all Android JVM tests with:

```sh
bun run test:android
```

This runs `test:android:prepare` followed by the Gradle unit-test task. Tests
belong in `android/src/test`. Pure Kotlin behavior can use JUnit directly; tests
that need Android framework behavior should use the AndroidX test runner and
Robolectric.

Every Robolectric test class must declare an explicit `@Config(sdk = [...])`.
This keeps its intended Android coverage visible and prevents it from
accidentally inheriting the module's target SDK.

## Android instrumentation tests

Prepare the native project, verify that the instrumentation APK compiles, and
then run it on a connected emulator or device:

```sh
bun run test:android:prepare
bun run test:android:instrumented:assemble
bun run test:android:instrumented
```

Instrumentation tests belong in `android/src/androidTest`. Prefer JVM or
Robolectric coverage when Android framework behavior can be reproduced there.

## Native bottom-anchored slot

Run the Android host test against the generated Java delegate and Robolectric
after preparing the example project:

```sh
./example/android/gradlew --project-dir example/android \
  :swmansion_react-native-bottom-sheet:testDebugUnitTest --tests '*BottomSheetHostViewAnchorTest'
```

On an Apple silicon Mac with Xcode, run the UIKit host test through Mac Catalyst
without starting a simulator:

```sh
test_dir=$(mktemp -d)
sdk=$(xcrun --sdk macosx --show-sdk-path)
swiftc -sdk "$sdk" -target arm64-apple-ios16.0-macabi \
  -F "$sdk/System/iOSSupport/System/Library/Frameworks" \
  -I "$sdk/System/iOSSupport/usr/include" \
  ios/HostingView/*.swift tests/ios/BottomSheetHostSettleTests.swift -o "$test_dir/host-test"
"$test_dir/host-test"
```
