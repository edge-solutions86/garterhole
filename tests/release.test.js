import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { versionsFound, releaseProblems } from '../scripts/release.mjs';
import { VERSION, PRODUCT, buildLabel } from '../src/version.js';

/**
 * The version is written in three places and read from two of them at run time.
 * These tests are the reason it cannot quietly disagree with itself.
 */

describe('versionsFound', () => {
  it('looks in all three places', () => {
    expect(versionsFound().map((entry) => entry.where)).toEqual([
      'package.json',
      'src/version.js',
      'CHANGELOG.md',
    ]);
  });

  it('finds a version in each of them', () => {
    for (const entry of versionsFound()) {
      expect(entry.version, `${entry.where} has no version`).toMatch(/^\d+\.\d+\.\d+$/);
    }
  });

  it('finds the same version in each of them', () => {
    const distinct = new Set(versionsFound().map((entry) => entry.version));
    expect(distinct.size).toBe(1);
  });
});

describe('releaseProblems', () => {
  it('finds nothing wrong with this release', () => {
    expect(releaseProblems()).toEqual([]);
  });
});

describe('the version itself', () => {
  it('is a version number', () => {
    expect(VERSION).toMatch(/^\d+\.\d+\.\d+$/);
  });

  it('is what the manifest says', () => {
    const manifest = JSON.parse(readFileSync('package.json', 'utf8'));
    expect(manifest.version).toBe(VERSION);
  });

  it('is what the banner says', () => {
    expect(buildLabel()).toBe(`${PRODUCT}/${VERSION}`);
  });

  it('is mentioned in the changelog', () => {
    expect(readFileSync('CHANGELOG.md', 'utf8')).toContain(`## ${VERSION}`);
  });
});
