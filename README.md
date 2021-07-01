# NekoAgent

## Features

- Allow sand duplication.
- Prevent obsidian spikes reset.
- Allow StoneCutter to hurt player.
- Allow Shulkers spawn in end cities.

## Usage

```bash
java -javaagent:NekoAgent-1.0-SNAPSHOT.jar -jar paper.jar
```

## Disable some features

```bash
java -javaagent:NekoAgent-1.0-SNAPSHOT.jar=disallowSandDuplication -jar paper.jar
java -javaagent:NekoAgent-1.0-SNAPSHOT.jar=allowObsidianSpikesReset -jar paper.jar
java -javaagent:NekoAgent-1.0-SNAPSHOT.jar=disallowSandDuplication+allowObsidianSpikesReset -jar paper.jar
```

## Build

```bash
gradlew jar
```

## Author

Shirasawa

## License

[MIT](./LICENSE)
