# NekoAgent

## Usage

```bash
java -javaagent:NekoAgent-1.0-SNAPSHOT.jar -jar paper.jar
```

## Disable some functions

```bash
java -javaagent:NekoAgent-1.0-SNAPSHOT.jar=allowSandDuplication -jar paper.jar
java -javaagent:NekoAgent-1.0-SNAPSHOT.jar=allowSandDuplication+allowEndPlatform -jar paper.jar
```

## Build

```bash
gradlew jar
```

## Author

Shirasawa

## License

[MIT](./LICENSE)
