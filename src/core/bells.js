import { MAX_STAGE } from './stage.js';

/**
 * Bells are written with a single character each so that a row is a string a
 * ringer can read at a glance. The sequence is the one used by every printed
 * method collection: digits up to nine, then zero for the tenth bell, then
 * letters for the rest.
 */
const BELL_SYMBOLS = '1234567890ETABCD';

const SYMBOL_TO_INDEX = new Map();
for (let i = 0; i < BELL_SYMBOLS.length; i += 1) {
  SYMBOL_TO_INDEX.set(BELL_SYMBOLS[i], i);
}

/**
 * Lower case letters are accepted on input because they are what people type.
 */
for (let i = 0; i < BELL_SYMBOLS.length; i += 1) {
  const symbol = BELL_SYMBOLS[i];
  const lower = symbol.toLowerCase();
  if (lower !== symbol) {
    SYMBOL_TO_INDEX.set(lower, i);
  }
}

/**
 * The character used to write a given bell.
 *
 * @param {number} bell one based bell number, the treble being 1
 * @returns {string} a single character
 */
export function bellSymbol(bell) {
  if (!Number.isInteger(bell) || bell < 1 || bell > MAX_STAGE) {
    throw new RangeError(`bell ${bell} is outside 1..${MAX_STAGE}`);
  }
  return BELL_SYMBOLS[bell - 1];
}

/**
 * The bell a character stands for.
 *
 * @param {string} symbol a single character, either case
 * @returns {number} one based bell number
 */
export function bellFromSymbol(symbol) {
  const index = SYMBOL_TO_INDEX.get(symbol);
  if (index === undefined) {
    throw new RangeError(`${JSON.stringify(symbol)} is not a bell symbol`);
  }
  return index + 1;
}

/**
 * @param {string} symbol
 * @returns {boolean} true when the character names a bell
 */
export function isBellSymbol(symbol) {
  return SYMBOL_TO_INDEX.has(symbol);
}

/**
 * The symbols for a whole stage, in order, as one string.
 *
 * @param {number} stage
 * @returns {string}
 */
export function symbolsForStage(stage) {
  if (!Number.isInteger(stage) || stage < 1 || stage > MAX_STAGE) {
    throw new RangeError(`stage ${stage} is outside 1..${MAX_STAGE}`);
  }
  return BELL_SYMBOLS.slice(0, stage);
}

/**
 * Turn a run of bell symbols into the bells they name.
 *
 * @param {string} text
 * @returns {number[]}
 */
export function bellsFromSymbols(text) {
  const bells = [];
  for (const character of String(text)) {
    bells.push(bellFromSymbol(character));
  }
  return bells;
}

/**
 * Write a sequence of bells back out as symbols.
 *
 * @param {Iterable<number>} bells
 * @returns {string}
 */
export function symbolsFromBells(bells) {
  let text = '';
  for (const bell of bells) {
    text += bellSymbol(bell);
  }
  return text;
}

/**
 * The tenor of a stage. Ringers say "the tenor" far more often than they say
 * the number, and the phrase turns up in call definitions and in the striking
 * analysis, so it earns a name.
 *
 * @param {number} stage
 * @returns {number}
 */
export function tenorOf(stage) {
  return stage;
}

/**
 * The treble is always bell one, whatever the stage. The constant exists so
 * that code reads as ringing rather than as arithmetic.
 */
export const TREBLE = 1;
