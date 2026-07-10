# garterhole

Tooling for change ringing bands: method construction, composition proving,
striking analysis, and the tower admin that surrounds them.

Written for a tower secretary who needs answers that are correct rather than
approximately correct. A touch reported true has been proved row by row. A
method reported as Surprise Major has been classified from its place notation,
not from the name somebody typed in. A performance record that says it is a peal
of Minor has been checked against the rule that says a peal of Minor is a whole
number of extents.

## Running it

```
npm install
npm test
npm run verify      # lint, format, surface, tests and coverage
npx garterhole --help
```

Node 20.11 or newer. There are no runtime dependencies; everything the service
needs at run time is in the standard library. That is deliberate, because the
towers this runs for install it on hardware nobody wants to babysit.

## The command line

```
garterhole method add --name Cambridge --stage 8 --notation x38x14x1258x36x14x58x16x78,12
garterhole prove --title "Cambridge Surprise Major" --touch "3(MWH)"
garterhole tower add --name "St Mary" --place Bridgend --weights 4-0-0,4-2-7,5-1-14,6-0-0,8-2-21,12-1-4
garterhole records file --tower "St Mary" --title "Plain Bob Minor" --changes 1260 --band Alice,Bob,Carol,Dan,Erin,Frank
garterhole records report --format csv
```

Every command takes `--json` for a script and `--quiet` to say less.

## The service

```
GARTERHOLE_DATA=./data garterhole serve
```

The routes, the commands and the settings are listed in
[docs/surface.md](docs/surface.md), which is written out of the code by
`npm run surface` rather than by hand.

## Layout

| directory         | what lives there                                           |
| ----------------- | ---------------------------------------------------------- |
| `src/core`        | rows, changes, transposition, the permutation algebra      |
| `src/notation`    | place notation: tokenising, parsing, expansion, formatting |
| `src/method`      | methods, hunt bells, lead heads, classification            |
| `src/composition` | calls, touches, proving, falseness, music, searching       |
| `src/striking`    | strike timing streams and accuracy analysis                |
| `src/tower`       | towers, bells, ringers, availability, assignment           |
| `src/records`     | performance records, validation, statistics, reports       |
| `src/store`       | the append only journal and the repositories over it       |
| `src/http`        | the HTTP surface                                           |
| `src/cli`         | the command line surface                                   |

Tests live in `tests/` and mirror `src/`.

## How it stores things

Everything is a fold over an append only journal of JSON lines, each with a
checksum in front of it. Nothing is changed in place, so a write either lands
whole or leaves a line the reader rejects, and the state can always be rebuilt
from the file. Snapshots exist only so that start up does not have to replay
everything, and throwing one away costs nothing but time.

## Conventions

Bells are numbered from the treble. A row is a permutation written with the
usual symbols, so `1234567890ET` covers stages up to Maximus. Place notation
uses `x` for a cross change and `.` as a separator, with `,` marking the
palindromic fold. Bell weights are written the way a tower board writes them:
`23-2-11` is twenty three hundredweight, two quarters and eleven pounds.

Errors come from a small taxonomy, and the HTTP layer maps the taxonomy onto
status codes rather than reading messages. Anything outside the taxonomy is a
bug rather than a rejected request, and answers 500 without saying why.
