# garterhole

Tooling for change ringing bands: method construction, composition proving,
striking analysis, and the tower admin that surrounds them.

Written for a tower secretary who needs answers that are correct rather than
approximately correct. A touch reported true has been proved row by row. A
method reported as Surprise Major has been classified from its place notation,
not from the name somebody typed in. A performance record that says it is a peal
of Minor has been checked against the rule that says a peal of Minor is a whole
number of extents.

## Tech stack

- Java 21, built with Gradle (Groovy DSL) through the wrapper
- picocli for the command line
- Jackson databind for JSON, written byte for byte the way `JSON.stringify`
  writes it, because the journal checksums every line
- the JDK's own `com.sun.net.httpserver` for the HTTP surface, with no web
  framework
- JUnit 5 and AssertJ for the tests, JaCoCo for coverage, Spotless with the
  Palantir format for formatting

There is nothing else at run time. That is deliberate, because the towers this
runs for install it on hardware nobody wants to babysit.

## Building and testing

```
./gradlew build          # compile, format check, tests, coverage gate, surface and release checks
./gradlew test           # the tests alone
./gradlew spotlessApply  # put the formatting right
./gradlew installDist    # a runnable build/install/garterhole/bin/garterhole
```

`check` fails when line coverage drops below 80%, when `docs/surface.md` is not
what the code would write, or when the build file, `Version` and the changelog
disagree about the version. The repository's own tooling has tasks of its own:

| task | what it does |
| --- | --- |
| `./gradlew surface` | writes `docs/surface.md` from the routes, the commands and the settings |
| `./gradlew surfaceCheck` | fails when `docs/surface.md` is out of date |
| `./gradlew releaseCheck` | fails when the version disagrees with itself |
| `./gradlew smoke` | starts the service on a free port, asks it a few questions and stops it |
| `./gradlew bench` | times proving, falseness and the touch search; it prints and never fails |

## The command line

```
garterhole method add --name Cambridge --stage 8 --notation x38x14x1258x36x14x58x16x78,12
garterhole method list --stage 8 --class Surprise
garterhole prove --title "Cambridge Surprise Major" --touch "3(MWH)"
garterhole lead --title Plain Bob Major
garterhole tower add --name "St Mary" --place Bridgend --weights 4-0-0,4-2-7,5-1-14,6-0-0,8-2-21,12-1-4
garterhole records file --tower "St Mary" --title "Plain Bob Minor" --changes 1260 --band Alice,Bob,Carol,Dan,Erin,Frank
garterhole records report --format csv
garterhole --help
```

Every command takes `--json` for a script and `--quiet` to say less. A method
title may be typed without quotes; the words are joined back together. An
unknown flag is refused by name rather than ignored. The command exits 0 when it
did what was asked, 1 when it was refused (the reason goes to standard error),
and 2 when the line names no command.

## The service

```
GARTERHOLE_DATA=./data garterhole serve --port 8080
```

| method | path | what it does |
| --- | --- | --- |
| GET | `/health` | that the service is up, its version and how long it has been up |
| GET | `/ready` | whether the store answers, and how much it holds |
| GET | `/routes` | every route, one per line |
| GET | `/methods` | the method library, by `stage` and `class`, a page at a time |
| POST | `/methods` | file a method from its name, stage and notation |
| GET | `/methods/:title` | one method, with the facts worked out from its notation |
| DELETE | `/methods/:title` | take a method out of the library |
| POST | `/methods/:title/analyse` | ring a touch of the method, prove it and score its music |
| GET | `/towers` | the towers |
| POST | `/towers` | file a tower and its bells |
| GET | `/towers/:id` | one tower |
| GET | `/towers/:id/bells` | its bells, with their weights as a tower board writes them |
| DELETE | `/towers/:id` | take a tower out |
| GET | `/performances` | the performances, by `tower` and `ringer` |
| POST | `/performances` | file a performance, checking it first |
| GET | `/performances/:id` | one performance |
| POST | `/performances/check` | check a performance without filing it |
| GET | `/report` | the performances as `text`, `list`, `csv` or `json` |
| GET | `/statistics` | totals by ringer and by tower |
| POST | `/striking` | analyse a recording of strike times |
| POST | `/striking/report` | the same analysis as a written report |
| POST | `/striking/blows.csv` | every blow and its error |
| POST | `/striking/bells.csv` | each bell's accuracy |

Errors come from a small taxonomy, and the HTTP layer maps it onto status codes
rather than reading messages: a lookup that finds nothing is 404, a duplicate is
409, and a document that breaks its own rules is 400 with every problem listed.
Anything outside the taxonomy is a bug rather than a rejected request, and
answers 500 without saying why.

The settings come from the environment, and every one has a default that works:

| variable | default |
| --- | --- |
| `GARTERHOLE_PORT` | `8080` (0 lets the machine choose) |
| `GARTERHOLE_HOST` | `127.0.0.1` |
| `GARTERHOLE_DATA` | `data` |
| `GARTERHOLE_LOG_LEVEL` | `info` (`debug`, `info`, `warn`, `error` or `silent`) |
| `GARTERHOLE_RATE_LIMIT` | `120` requests a minute from one caller |
| `GARTERHOLE_READ_ONLY` | `false` |

[docs/surface.md](docs/surface.md) lists the routes, the commands and the
settings again, written out of the code by `./gradlew surface` rather than by
hand.

## Layout

Everything is under `io.github.rkaushikdev.garterhole`, and the tests mirror the
main sources package for package.

| package | what lives there |
| --- | --- |
| `core` | rows, changes, transposition, the permutation algebra |
| `notation` | place notation: tokenising, parsing, expansion, formatting |
| `method` | methods, hunt bells, lead heads, classification, the method library |
| `composition` | calls, touches, proving, falseness, part structure, searching |
| `composition.music` | music patterns, runs and scoring |
| `striking` | strike timing streams and accuracy analysis |
| `tower` | towers, bells, ringers, availability, assignment, practice planning |
| `records` | performance records, validation, statistics, reports |
| `store` | the append only journal and the repository over it |
| `http` | the HTTP surface, its middleware and its routes |
| `cli` | the command line, its commands and the `Main` entry point |
| `config`, `validation`, `util` | settings, schemas and the shared helpers |
| `tools` | the repository's own tooling, run by the Gradle tasks above |

The root package holds the error taxonomy, `Version` and the `Service` that both
surfaces go through.

## How it stores things

Everything is a fold over an append only journal of JSON lines, each with a
checksum in front of it. Nothing is changed in place, so a write either lands
whole or leaves a line the reader rejects, and the state can always be rebuilt
from the file. Snapshots exist only so that start up does not have to replay
everything, and throwing one away costs nothing but time. A lock file in the
data directory stops two copies writing the same journal.

## Conventions

Bells are numbered from the treble. A row is a permutation written with the
usual symbols, so `1234567890ET` covers stages up to Maximus. Place notation
uses `x` for a cross change and `.` as a separator, with `,` marking the
palindromic fold. Bell weights are written the way a tower board writes them:
`23-2-11` is twenty three hundredweight, two quarters and eleven pounds.

## History

garterhole was first written in JavaScript, for Node, and was ported to Java 21
in November 2026. The journal format did not change, so a data directory
written by the JavaScript version loads in the Java one.
