# ModelTap

![ModelTap](app/src/main/res/drawable-nodpi/modeltap_logo_horizontal.png)

[www.modeltap.cn](https://www.modeltap.cn)

ModelTap is an Android client for using OpenAI-compatible AI APIs from your own configured providers.

## Features

- Chat with OpenAI-compatible models
- Configure multiple API endpoints and models
- Store API keys locally on the device
- Optional Tavily web search integration
- Image generation workflow
- Local chat history, session management, and usage statistics

## Build

Requirements:

- Android Studio or Android SDK
- JDK 17

Debug build:

```powershell
.\gradlew.bat assembleDebug
```

Unit tests:

```powershell
.\gradlew.bat test
```

## Release Signing

Release signing files are intentionally not committed.

For a signed release build, create `release-signing.properties` locally:

```properties
storeFile=path/to/release.keystore
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

Keep keystores and signing properties private.

## API Keys

No provider API keys are bundled with the app. Users configure their own API keys in the app.
