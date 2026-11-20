/**
 * The repository's own tooling, each run by a Gradle task: {@code bench} times
 * the slow parts, {@code smoke} asks a running service a few questions,
 * {@code surface} writes {@code docs/surface.md} from the code, and
 * {@code releaseCheck} and {@code surfaceCheck} fail the build when the version
 * or the surface document has drifted.
 *
 * <p>Every tool does its work in {@code main} and nowhere else, so the tests can
 * call the pieces without anything being started, written or printed.
 */
package io.github.rkaushikdev.garterhole.tools;
