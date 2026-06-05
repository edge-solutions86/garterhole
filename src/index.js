/**
 * What this library offers.
 *
 * Everything here is exported on purpose and is meant to stay exported.
 * Anything not listed is an implementation detail, and reaching into it will
 * break without warning.
 *
 * The order is the order of the layers: the row algebra first, then notation,
 * methods, compositions, striking, towers, records, and the surfaces last.
 */

export { VERSION, PRODUCT, buildLabel } from './version.js';

export {
  GarterholeError,
  ValidationError,
  NotationError,
  NotFoundError,
  ConflictError,
  StorageError,
  ConfigError,
  isGarterholeError,
} from './errors.js';

export {
  MIN_STAGE,
  MAX_STAGE,
  isSupportedStage,
  stageName,
  stageFromName,
  isOddStage,
  hasCover,
  bellsInTower,
  supportedStages,
} from './core/stage.js';

export {
  TREBLE,
  bellSymbol,
  bellFromSymbol,
  isBellSymbol,
  symbolsForStage,
  bellsFromSymbols,
  symbolsFromBells,
  tenorOf,
} from './core/bells.js';

export { Row, rounds } from './core/row.js';
export { parseRow, parseRows, tryParseRow } from './core/rowParse.js';
export { Change } from './core/change.js';
export { RowBlock, changeBetween } from './core/rowBlock.js';
export { transpose, inverse, permutationBetween, power, order } from './core/transposition.js';
export { cycles, cycleLengths, orderOf, parity, isInCourse, fixedBells } from './core/cycles.js';
export { queens, kings, tittums, backrounds, namedRows, nameOfRow } from './core/namedRows.js';

export { tokenize } from './notation/tokenizer.js';
export { parseNotation, parseChanges } from './notation/parser.js';
export { expandNotation, isPalindromic, symmetryPoints } from './notation/expand.js';
export { writeChanges, writeFolded, writeLeadLines } from './notation/format.js';

export { Method, method } from './method/method.js';
export { huntBells, workingBells, isPrinciple, pathOf } from './method/huntBells.js';
export { plainCourse, leadsInCourse, courseLength } from './method/plainCourse.js';
export { coursePath, workOf, pathByLead } from './method/pathTrace.js';
export { symmetryOf, isDouble } from './method/symmetry.js';
export { classify, baseClass } from './method/classify.js';
export { buildTitle, parseTitle, titleOf } from './method/title.js';
export { MethodLibrary } from './method/library.js';
export { search as searchLibrary, spliceGroups } from './method/librarySearch.js';
export { methodToJson, methodFromJson } from './method/serialise.js';
export { canonicalForm, groupBySameLead } from './method/canonical.js';
export { methodStats, placeBellOrder } from './method/stats.js';

export { Call, call } from './composition/call.js';
export { standardCalls } from './composition/callSet.js';
export { CallRegistry, defaultRegistry } from './composition/callRegistry.js';
export { positionAt, namedPositions } from './composition/callingPosition.js';
export { parseTouch } from './composition/touchParser.js';
export { expandTouch, touchLength } from './composition/touchExpander.js';
export { prove, proveTouch, isExtent, TruthTracker } from './composition/prover.js';
export { classifyLength, checkLength, pealLength } from './composition/lengths.js';
export { falseCourseHeads, tenorsTogetherFalseCourseHeads } from './composition/falseness.js';
export { partHeads, detectParts } from './composition/partStructure.js';
export { scoreMusic, musicSummary } from './composition/music/scorer.js';
export { analyseTouch, reportLines as touchReportLines } from './composition/analysis.js';
export { Composition } from './composition/composition.js';
export { searchTouches } from './composition/search.js';

export { StrikeStream, streamFrom } from './striking/stream.js';
export { TimingModel, fitModel, modelForPealSpeed } from './striking/timingModel.js';
export { StrikingSession, analyseStriking } from './striking/session.js';
export { reportLines as strikingReportLines } from './striking/report.js';
export { asDocument as strikingDocument } from './striking/export.js';

export { Bell, bellWeighing } from './tower/bell.js';
export { Tower } from './tower/tower.js';
export { Ringer, ringer } from './tower/ringer.js';
export { CompetencyRegister } from './tower/competency.js';
export { Interval, IntervalSet, interval } from './tower/interval.js';
export { Diary } from './tower/availability.js';
export { assignBells } from './tower/assignment.js';
export { planPractice } from './tower/practice.js';
export { Roster } from './tower/roster.js';

export { Performance, performance } from './records/performance.js';
export { validatePerformance } from './records/validate.js';
export { PerformanceCollection } from './records/collection.js';
export { totals, byRinger, byTower } from './records/statistics.js';
export { writerNamed, formats } from './records/writers.js';
export { leaderboard } from './records/leaderboard.js';
export { readFootnote } from './records/footnote.js';

export { Journal } from './store/journal.js';
export { Snapshot } from './store/snapshot.js';
export { Repository } from './store/repository.js';
export { UnitOfWork, inUnitOfWork } from './store/unitOfWork.js';
export { backup, restore } from './store/backup.js';

export { Service } from './service.js';
export { buildApp, start } from './http/app.js';
export { run as runCommandLine } from './cli/program.js';
export { loadConfig } from './config/config.js';
export { Logger } from './util/logger.js';
