# OminousBottleFarm 1.0.1

Paper plugin source project. Requires Java 25 and Gradle 9.x to build.

## Build
Run `gradle build`. The JAR is created in `build/libs/`.

## GitHub Actions
Upload this project to a GitHub repository. The workflow under `.github/workflows/build.yml`
runs on pushes to `main` and can also be started manually from Actions. Download the artifact
from the completed workflow run.

## In-game setup
Stand on the platform at one corner and run `/obfarm pos1`.
Stand on the opposite corner at the same floor height and run `/obfarm pos2`.
Run `/obfarm set`, then `/obfarm start`.

Commands: `/obfarm pos1`, `pos2`, `set`, `start`, `stop`, `status`, `reload`.

The plugin spawns tagged pillagers on the selected solid floor area when a player is within
the configured activation radius. Captains are selected using `captain-chance-percent` and
drop an Ominous Bottle with a randomly selected amplifier from 0 to 4.
