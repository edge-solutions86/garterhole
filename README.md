# garterhole

Tooling for change ringing bands: method construction, composition proving,
striking analysis, and the tower admin that surrounds them.

The library is written for a tower secretary who needs answers that are correct
rather than approximately correct. A touch that is reported true has been proved
row by row. A method that is reported as Surprise Major has been classified from
its place notation, not from the name somebody typed in.

## Layout

| directory         | what lives there                                           |
| ----------------- | ---------------------------------------------------------- |
| `src/core`        | rows, changes, transposition, the permutation algebra      |
| `src/notation`    | place notation: tokenising, parsing, expansion, formatting |
| `src/method`      | methods, hunt bells, lead heads, classification            |
| `src/composition` | calls, touches, proving, falseness, music                  |
| `src/striking`    | strike timing streams and accuracy analysis                |
| `src/tower`       | towers, bells, ringers, availability, assignment           |
| `src/records`     | performance records, validation, statistics                |
| `src/store`       | the append only journal and the repositories over it       |
| `src/http`        | the HTTP surface                                           |
| `src/cli`         | the command line surface                                   |

Tests live in `tests/` and mirror the `src/` tree.

## Running

```
npm install
npm test
npm run lint
```

Node 20.11 or newer. There are no runtime dependencies; everything the service
needs at run time is in the standard library. That is a deliberate choice, since
the towers this runs for install it on hardware nobody wants to babysit.

## Conventions

Bells are numbered from the treble. A row is a permutation written with the
usual symbols, so `1234567890ET` covers stages up to maximus. Place notation
uses `x` for a cross change and `.` as a separator, with `,` marking the
palindromic fold.
