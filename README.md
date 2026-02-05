# OOP Game (Java)

Small object-oriented game project built step by step for a university OOP course.
The project progresses by "worlds" and "levels" (World 1, World 2, ...).

## How to run (World 1)
From `java/app/src`:

```
javac Main.java Player.java
java Main
```

## Structure
- `src/Main.java` - simple test harness for the current world.
- `src/Player.java` - player model (World 1).
- `src/Level.java` - level model (World 2+).

## Javadoc
Generate HTML docs from `java/app/src`:

```
javadoc -d docs Player.java Main.java
```

## Notes
This repo tracks the source files only; generated files and IDE settings are ignored.
