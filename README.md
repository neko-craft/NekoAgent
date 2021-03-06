# NekoAgent

## Features

- Allow sand duplication.
- Prevent obsidian spikes reset.
- Allow StoneCutter to hurt player.
- Allow Shulkers spawn in end cities.
- Modify server mod name.

## Usage

```bash
java -javaagent:NekoAgent-1.0-SNAPSHOT.jar -jar paper.jar
```

## Disable some features

```bash
java -javaagent:NekoAgent-1.0-SNAPSHOT.jar=enableSandDuplication -jar paper.jar
java -javaagent:NekoAgent-1.0-SNAPSHOT.jar=disableObsidianSpikesReset -jar paper.jar
java -javaagent:NekoAgent-1.0-SNAPSHOT.jar=enableStoneCutterDamage+enableShulkerSpawningInEndCities -jar paper.jar
```

## Feature flags

- enableSandDuplication
- disableObsidianSpikesReset
- enableStoneCutterDamage
- enableShulkerSpawningInEndCities

## Build

```bash
gradlew jar
```

## Author

Shirasawa

## License

[MIT](./LICENSE)
