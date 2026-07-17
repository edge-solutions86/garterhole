/**
 * Run with `node scripts/release.mjs`.
 *
 * There is no shebang here on purpose. These scripts are imported by the tests
 * as well as run from the command line, and a shebang on a module that gets
 * imported is read as the first line of the program rather than as a comment
 * once a checkout has converted the line endings. The entry point in `bin/` keeps its shebang, because nothing
 * imports it.
 *
 * Check that a release says the same thing everywhere.
 *
 * Three files carry the version: the manifest, the module that the banner and
 * the health route read, and the changelog. They drift, and they drift quietly,
 * because nothing breaks when they disagree. A service reporting a version it
 * is not is a bad afternoon during an incident, so this refuses to let them.
 */
import { readFileSync } from 'node:fs';
import process from 'node:process';
import { VERSION } from '../src/version.js';

/**
 * Everywhere the version is written, and what it says.
 *
 * @returns {{ where: string, version: string | null }[]}
 */
export function versionsFound() {
  const manifest = JSON.parse(readFileSync('package.json', 'utf8'));
  const changelog = readFileSync('CHANGELOG.md', 'utf8');
  const heading = changelog.match(/^##\s+(\d+\.\d+\.\d+)\s*$/m);
  return [
    { where: 'package.json', version: manifest.version ?? null },
    { where: 'src/version.js', version: VERSION },
    { where: 'CHANGELOG.md', version: heading === null ? null : heading[1] },
  ];
}

/**
 * What is wrong with the release, if anything.
 *
 * @returns {string[]}
 */
export function releaseProblems() {
  const found = versionsFound();
  const problems = [];
  for (const entry of found) {
    if (entry.version === null) {
      problems.push(`${entry.where} does not say what version this is`);
    }
  }
  const distinct = new Set(found.map((entry) => entry.version).filter((value) => value !== null));
  if (distinct.size > 1) {
    problems.push(
      `the version disagrees: ${found
        .map((entry) => `${entry.where} says ${entry.version}`)
        .join(', ')}`,
    );
  }
  if (!/^\d+\.\d+\.\d+$/.test(VERSION)) {
    problems.push(`${VERSION} is not a version number`);
  }
  return problems;
}

if (process.argv[1]?.endsWith('release.mjs')) {
  const problems = releaseProblems();
  for (const problem of problems) {
    process.stderr.write(`${problem}\n`);
  }
  if (problems.length > 0) {
    process.exitCode = 1;
  } else {
    process.stdout.write(`everything agrees that this is ${VERSION}\n`);
  }
}
