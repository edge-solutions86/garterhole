package io.github.rkaushikdev.garterhole.composition;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.RowBlock;
import io.github.rkaushikdev.garterhole.method.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Looking for a touch.
 *
 * <p>This is a depth first search over the calls, one lead at a time, keeping
 * the rows in a {@link TruthTracker} as it goes. A branch that repeats a row is
 * abandoned at once rather than being rung out and proved afterwards, which is
 * the whole reason the tracker exists.
 *
 * <p>It is not a composing program. It will find the touches of a hundred and
 * twenty changes that a tower rings on a practice night, and it will not find a
 * peal composition, because the search space of a peal is far past what a
 * depth first walk can cover. Saying so here is better than letting somebody
 * find out by leaving it running overnight.
 */
public final class TouchSearch {

    /** The most leads a search will ring down one branch. */
    public static final int MAX_DEPTH = 60;

    private static final int DEFAULT_MAX_LEADS = 12;
    private static final int DEFAULT_LIMIT = 20;

    private TouchSearch() {}

    /**
     * A touch the search found.
     *
     * @param calling the call symbol of every lead, for example {@code -..-..}
     * @param changes how long it is
     * @param leads how many leads it has
     * @param notation the method's notation written out in full
     */
    public record FoundTouch(String calling, int changes, int leads, String notation) {}

    /**
     * What to search for. Anything not given takes its default: the standard
     * calls for the method, any length, twelve leads deep and twenty touches.
     */
    public static final class Request {
        private final Method method;
        private final Map<String, Call> calls;
        private final Set<Integer> lengths;
        private final int maxLeads;
        private final int limit;

        private Request(Method method, Map<String, Call> calls, Set<Integer> lengths, int maxLeads, int limit) {
            this.method = method;
            this.calls = calls;
            this.lengths = lengths;
            this.maxLeads = maxLeads;
            this.limit = limit;
        }

        /**
         * @param method the method to search for touches of
         * @return a request with everything else at its default
         */
        public static Request of(Method method) {
            return new Request(method, null, null, DEFAULT_MAX_LEADS, DEFAULT_LIMIT);
        }

        /**
         * @param chosen the calls to try at every lead, by role, tried in their order
         * @return this request with its own calls
         */
        public Request withCalls(Map<String, Call> chosen) {
            return new Request(method, chosen, lengths, maxLeads, limit);
        }

        /**
         * @param wanted the lengths worth reporting, in changes
         * @return this request keeping only touches of those lengths
         */
        public Request withLengths(Collection<Integer> wanted) {
            return new Request(method, calls, Set.copyOf(wanted), maxLeads, limit);
        }

        /**
         * @param leads how deep to search, from one to {@link #MAX_DEPTH}
         * @return this request searching that deep
         */
        public Request withMaxLeads(int leads) {
            return new Request(method, calls, lengths, leads, limit);
        }

        /**
         * @param touches how many touches to find before stopping
         * @return this request stopping there
         */
        public Request withLimit(int touches) {
            return new Request(method, calls, lengths, maxLeads, touches);
        }
    }

    /**
     * Search for true touches that come round.
     *
     * @param request what to search for
     * @return the touches found, in the order the search reached them
     * @throws ValidationException when there is no method, or the depth or limit is out of range
     */
    public static List<FoundTouch> searchTouches(Request request) {
        if (request == null || request.method == null) {
            throw new ValidationException("a Method is needed to search for touches of it");
        }
        Method method = request.method;
        Map<String, Call> calls = request.calls != null ? request.calls : CallSet.standardCalls(method);
        if (request.maxLeads < 1 || request.maxLeads > MAX_DEPTH) {
            throw new ValidationException("a search of " + request.maxLeads + " leads is outside 1.." + MAX_DEPTH);
        }
        if (request.limit < 1) {
            throw new ValidationException("a search that returns " + request.limit + " touches is not a search");
        }
        Row start = Row.rounds(method.stage());
        Walk walk = new Walk(method, List.copyOf(calls.values()), request);
        walk.tracker.add(start);
        walk.from(start, new ArrayList<>(), 0);
        return List.copyOf(walk.found);
    }

    /**
     * @param found touches a search found
     * @return the shortest of them, the first on a tie, when there are any
     */
    public static Optional<FoundTouch> shortest(List<FoundTouch> found) {
        FoundTouch best = null;
        for (FoundTouch touch : found) {
            if (best == null || touch.changes() < best.changes()) {
                best = touch;
            }
        }
        return Optional.ofNullable(best);
    }

    /** One search in progress. */
    private static final class Walk {
        private final Method method;
        private final List<Call> order;
        private final List<List<Change>> calledLeads;
        private final Request request;
        private final TruthTracker tracker = new TruthTracker();
        private final List<FoundTouch> found = new ArrayList<>();
        private final String notation;

        Walk(Method method, List<Call> order, Request request) {
            this.method = method;
            this.order = order;
            this.request = request;
            this.calledLeads =
                    order.stream().map(call -> call.applyToLead(method)).toList();
            this.notation = method.fullNotation();
        }

        /** Ring one lead with each call in turn, and either report a touch or carry on. */
        void from(Row row, List<String> calling, int leads) {
            if (found.size() >= request.limit || leads >= request.maxLeads) {
                return;
            }
            for (int choice = 0; choice < order.size(); choice += 1) {
                RowBlock lead = RowBlock.fromChanges(row, calledLeads.get(choice));
                List<Row> added = new ArrayList<>(lead.length());
                boolean stayedTrue = true;
                for (int index = 1; index < lead.length(); index += 1) {
                    Row next = lead.at(index);
                    if (index == lead.length() - 1 && next.isRounds()) {
                        break;
                    }
                    added.add(next);
                    if (!tracker.add(next)) {
                        stayedTrue = false;
                        break;
                    }
                }
                calling.add(order.get(choice).symbol());
                if (stayedTrue && lead.last().isRounds() && leads + 1 >= 2) {
                    report(calling, (leads + 1) * method.leadLength());
                } else if (stayedTrue) {
                    from(lead.last(), calling, leads + 1);
                }
                calling.remove(calling.size() - 1);
                for (int index = added.size() - 1; index >= 0; index -= 1) {
                    tracker.undo(added.get(index));
                }
            }
        }

        private void report(List<String> calling, int changes) {
            if (request.lengths != null && !request.lengths.contains(changes)) {
                return;
            }
            found.add(new FoundTouch(String.join("", calling), changes, calling.size(), notation));
        }
    }
}
