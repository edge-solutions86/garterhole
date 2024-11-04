/**
 * The published version of the library. Kept in a module of its own so that
 * both the CLI banner and the HTTP health route read the same value without
 * either of them importing the package manifest at run time.
 */
export const VERSION = '0.1.0';

/**
 * Name used in log lines, the CLI banner and the HTTP server header.
 */
export const PRODUCT = 'garterhole';

/**
 * A short human readable build label.
 *
 * @returns {string}
 */
export function buildLabel() {
  return `${PRODUCT}/${VERSION}`;
}
