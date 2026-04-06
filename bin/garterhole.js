#!/usr/bin/env node
/**
 * The entry point.
 *
 * It does three things and nothing else: drop the runtime and the script from
 * the arguments, run the command, and exit with what it gave back. Everything
 * that could fail lives in the program, where it can be tested.
 */
import process from 'node:process';
import { run } from '../src/cli/program.js';

const status = await run(process.argv.slice(2), { environment: process.env });
process.exitCode = status;
