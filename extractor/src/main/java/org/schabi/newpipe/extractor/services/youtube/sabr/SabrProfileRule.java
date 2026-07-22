package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** One loop-free rule composed only of Host-known predicates and actions. */
public final class SabrProfileRule {
    public enum Predicate {
        HAS_MEDIA,
        HAS_REDIRECT,
        HAS_PROTECTION_BOUNDARY,
        DEMANDED_SEGMENT_MISSING,
        BACKOFF_PRESENT,
        HAS_ERROR,
        RELOAD_REQUESTED,
        PUMP_MODE,
        FETCH_SEGMENT_MODE
    }

    public enum Action {
        CONTINUE(true),
        RETRY(true),
        APPLY_REDIRECT(false),
        REFRESH_PO_TOKEN(false),
        REQUIRE_PO_TOKEN(false),
        FAIL_SESSION(true),
        TRY_RELOAD(true);

        private final boolean terminal;

        Action(final boolean terminal) {
            this.terminal = terminal;
        }

        boolean isTerminal() {
            return terminal;
        }
    }

    @Nonnull private final List<Predicate> predicates;
    @Nonnull private final List<Action> actions;

    public SabrProfileRule(@Nonnull final List<Predicate> predicates,
                           @Nonnull final List<Action> actions) {
        if (predicates.size() > Predicate.values().length || actions.isEmpty()
                || actions.size() > Action.values().length) {
            throw new IllegalArgumentException("Invalid SABR behavior rule size");
        }
        final EnumSet<Predicate> uniquePredicates = EnumSet.noneOf(Predicate.class);
        final EnumSet<Action> uniqueActions = EnumSet.noneOf(Action.class);
        int terminals = 0;
        for (final Predicate predicate : predicates) {
            if (predicate == null || !uniquePredicates.add(predicate)) {
                throw new IllegalArgumentException("Duplicate SABR behavior predicate");
            }
        }
        for (final Action action : actions) {
            if (action == null || !uniqueActions.add(action)) {
                throw new IllegalArgumentException("Duplicate SABR behavior action");
            }
            if (action.isTerminal()) {
                terminals++;
            }
        }
        if (terminals != 1 || !actions.get(actions.size() - 1).isTerminal()) {
            throw new IllegalArgumentException("SABR behavior rule needs one terminal action");
        }
        this.predicates = immutableCopy(predicates);
        this.actions = immutableCopy(actions);
    }

    @Nonnull
    public List<Predicate> getPredicates() {
        return predicates;
    }

    @Nonnull
    public List<Action> getActions() {
        return actions;
    }

    void writeCanonical(@Nonnull final DataOutputStream output) throws IOException {
        output.writeByte(predicates.size());
        for (final Predicate predicate : predicates) {
            SabrCompatibilityProfile.writeString(output, predicate.name());
        }
        output.writeByte(actions.size());
        for (final Action action : actions) {
            SabrCompatibilityProfile.writeString(output, action.name());
        }
    }

    @Nonnull
    private static <T> List<T> immutableCopy(@Nonnull final List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
