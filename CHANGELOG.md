# Changelog

## 1.0.0

The first version anybody else should install.

### Added

- Rows, changes and place notation, with the three dialects of notation read
  into one form and written back out in one house style.
- Methods: hunt bells, lead heads, plain courses, blue lines, symmetry and
  classification, all worked out from the notation rather than recorded.
- A method library that refuses a duplicate title and a duplicate lead.
- Compositions: calls, calling positions, touches in either writing style,
  proving, false course heads, part structure and music scoring.
- A search for short true touches of a method.
- Striking analysis from a stream of strike times: a fitted timing model, per
  bell accuracy, named faults, and a rolling window over the touch.
- Towers, bells, ringers, competency, availability, an assignment solver and a
  practice planner.
- Performance records with validation, statistics, leaderboards and four
  output formats.
- An append only journal with checksums, snapshots, indexes, a lock file,
  compaction, backups and a unit of work.
- An HTTP surface and a command line surface, both over one service.

### Notes

- Odd stages count the changing bells only; the cover bell is not part of the
  stage. Getting this wrong made the extent of Doubles come out as twenty four
  rows, and the fix is why `hasCover` exists rather than `changingBells`.
- A peal on fewer than eight bells has to be a whole number of extents. A
  quarter peal does not, which is why a quarter of Doubles is 1260 and not 1320.
- There are no runtime dependencies and there is no plan to add any.
