import { Repository } from './store/repository.js';
import { inUnitOfWork } from './store/unitOfWork.js';
import { Method } from './method/method.js';
import { methodToJson, methodFromJson } from './method/serialise.js';
import { titleOf } from './method/title.js';
import { classify } from './method/classify.js';
import { Performance } from './records/performance.js';
import { validatePerformance } from './records/validate.js';
import { analyseTouch } from './composition/analysis.js';
import { ConflictError, NotFoundError, ValidationError } from './errors.js';
import { silentLogger } from './util/logger.js';

/**
 * What the service can be asked to do.
 *
 * Both the HTTP surface and the command line talk to this, and neither of them
 * talks to the repository directly. That is what stops the two drifting apart:
 * a rule added here, such as refusing a method whose title is already taken,
 * applies to both without anybody remembering to add it twice.
 *
 * Documents go in and out as plain objects. The domain classes are built when
 * something needs to be worked out and thrown away afterwards, which keeps the
 * stored shape independent of the classes.
 */

/** The collections the service uses. */
export const METHODS = 'methods';
export const TOWERS = 'towers';
export const PERFORMANCES = 'performances';

export class Service {
  /**
   * @param {{ repository: Repository, logger?: import('./util/logger.js').Logger }} options
   */
  constructor(options) {
    if (options === null || typeof options !== 'object') {
      throw new ValidationError('a service needs its options');
    }
    if (!(options.repository instanceof Repository)) {
      throw new ValidationError('a service needs a Repository');
    }
    this.repository = options.repository;
    this.logger = options.logger ?? silentLogger();
  }

  /**
   * Every method in the library, in title order.
   *
   * @returns {object[]}
   */
  listMethods() {
    return this.repository.all(METHODS).map((entry) => entry.document);
  }

  /**
   * @param {string} title
   * @returns {object}
   */
  getMethod(title) {
    const found = this.repository.find(METHODS, title);
    if (found === undefined) {
      throw new NotFoundError('method', title);
    }
    return found;
  }

  /**
   * Add a method, working its title and class out from the notation.
   *
   * @param {{ name: string, stage: number, notation: string }} definition
   * @returns {object}
   */
  addMethod(definition) {
    const method = new Method(definition);
    const title = titleOf(method);
    if (this.repository.has(METHODS, title)) {
      throw new ConflictError(`the library already holds ${JSON.stringify(title)}`);
    }
    const document = methodToJson(method);
    this.repository.put(METHODS, title, document);
    this.logger.info('method added', { title });
    return document;
  }

  /**
   * @param {string} title
   * @returns {boolean}
   */
  removeMethod(title) {
    return this.repository.delete(METHODS, title);
  }

  /**
   * Ring a touch of a method the library holds and report on it.
   *
   * @param {{ title: string, touch: string }} request
   * @returns {object}
   */
  analyse(request) {
    const method = methodFromJson(this.getMethod(request.title));
    const analysis = analyseTouch({ method, touch: request.touch });
    return {
      title: analysis.title,
      length: analysis.length,
      kind: analysis.kind,
      comesRound: analysis.comesRound,
      true: analysis.proof.isTrue,
      summary: analysis.summary,
      calls: analysis.calls,
      parts: analysis.parts,
      music: analysis.music,
    };
  }

  /**
   * The class and the derived facts of a method the library holds.
   *
   * @param {string} title
   * @returns {object}
   */
  describeMethod(title) {
    const method = methodFromJson(this.getMethod(title));
    return { ...methodToJson(method), ...classify(method) };
  }

  /**
   * @returns {object[]}
   */
  listTowers() {
    return this.repository.all(TOWERS).map((entry) => entry.document);
  }

  /**
   * @param {string} id
   * @returns {object}
   */
  getTower(id) {
    const found = this.repository.find(TOWERS, id);
    if (found === undefined) {
      throw new NotFoundError('tower', id);
    }
    return found;
  }

  /**
   * @param {string} id
   * @param {object} document
   * @returns {object}
   */
  putTower(id, document) {
    if (document === null || typeof document !== 'object') {
      throw new ValidationError('a tower must be an object');
    }
    this.repository.put(TOWERS, id, document);
    return document;
  }

  /**
   * @returns {object[]}
   */
  listPerformances() {
    return this.repository.all(PERFORMANCES).map((entry) => entry.document);
  }

  /**
   * File a performance, checking it first and refusing a repeat.
   *
   * @param {object} fields
   * @returns {{ id: string, warnings: string[] }}
   */
  filePerformance(fields) {
    const rung = new Performance({ ...fields, date: new Date(fields.date) });
    const verdict = validatePerformance(rung);
    if (!verdict.ok) {
      throw new ValidationError(verdict.problems.join('; '), { issues: verdict.problems });
    }
    if (this.repository.has(PERFORMANCES, rung.id)) {
      throw new ConflictError(`this performance is already filed as ${JSON.stringify(rung.id)}`);
    }
    return inUnitOfWork(this.repository, (unit) => {
      unit.put(PERFORMANCES, rung.id, {
        tower: rung.tower,
        date: rung.dateText,
        title: rung.title,
        changes: rung.changes,
        band: [...rung.band],
        conductor: rung.conductor,
        composer: rung.composer,
        kind: rung.kind,
      });
      this.logger.info('performance filed', { id: rung.id });
      return { id: rung.id, warnings: verdict.warnings };
    });
  }

  /**
   * What the service holds, for the health route.
   *
   * @returns {Record<string, number>}
   */
  counts() {
    return {
      methods: this.repository.count(METHODS),
      towers: this.repository.count(TOWERS),
      performances: this.repository.count(PERFORMANCES),
    };
  }
}
