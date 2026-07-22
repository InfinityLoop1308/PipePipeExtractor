package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Native evaluator for one immutable declarative compatibility client. */
public final class ProfiledSabrSessionPolicy implements SabrSessionPolicy {
    @Nonnull private final SabrCompatibilityProfileClient client;
    @Nonnull private final SabrProfileResponseMapper responseMapper;
    @Nonnull private final SabrMediaProtocol mediaProtocol;

    public ProfiledSabrSessionPolicy(@Nonnull final SabrCompatibilityProfileClient client) {
        this.client = client;
        responseMapper = new SabrProfileResponseMapper(client.getResponseMappings());
        mediaProtocol = new ProfiledSabrMediaProtocol(client);
    }

    @Nonnull
    @Override
    public Result evaluate(@Nonnull final State state, @Nonnull final Event event)
            throws SabrProtocolException {
        if (event instanceof RequestEvent) {
            final List<SabrProfileRequestField> template = state.getRequestNumber() == 0
                    ? client.getInitialRequest() : client.getFollowingRequest();
            return Result.request(state, state.getRequestNumber() == 0
                            ? ActionType.SEND_INITIAL_REQUEST
                            : ActionType.SEND_FOLLOW_UP_REQUEST,
                    ((RequestEvent) event).buildProfileRequest(template));
        }
        final ControlResponseEvent control = (ControlResponseEvent) event;
        final SabrProfileMappedResponse response = responseMapper.map(control.getResponse());
        final SabrProfileRule rule = selectRule(control, response);
        final List<Action> actions = new ArrayList<>();
        actions.add(new Action(ActionType.APPLY_RESPONSE_STATE));
        State next = state;
        boolean profileRedirect = false;
        for (final SabrProfileRule.Action configured : rule.getActions()) {
            final ActionType action = actionType(configured);
            if (action == ActionType.APPLY_REDIRECT) {
                final String redirectUrl = response.getRedirectUrl();
                if (redirectUrl == null || redirectUrl.isEmpty()) {
                    throw new SabrProtocolException("SABR profile applied an absent redirect");
                }
                profileRedirect = true;
                next = new State(state.getRequestNumber(), state.getRedirectCount() + 1,
                        state.getPoTokenRefreshes(), state.getReloads());
            }
            if (!isTerminal(action)) {
                actions.add(new Action(action));
            }
        }
        if (control.getMode() == ControlMode.PUMP && control.getSegmentCount() > 0) {
            next = new State(next.getRequestNumber(), 0, 0, next.getReloads());
            actions.add(new Action(ActionType.RESET_RECOVERY_BUDGETS));
        }
        final int backoff = Math.max(0, response.getBackoffMs());
        if (backoff > 0) {
            actions.add(new Action(control.shouldHonorBackoff()
                    ? ActionType.SLEEP_BACKOFF : ActionType.DEFER_BACKOFF));
        } else if (!control.shouldHonorBackoff()) {
            actions.add(new Action(ActionType.CLEAR_DEMAND_BACKOFF));
        }
        for (final SabrProfileRule.Action configured : rule.getActions()) {
            final ActionType action = actionType(configured);
            if (isTerminal(action)) {
                actions.add(new Action(action));
            }
        }
        final String error = actions.get(actions.size() - 1).getType()
                == ActionType.FAIL_SABR_ERROR
                ? response.getErrorDetails() == null
                ? "SABR compatibility profile terminated the session"
                : response.getErrorDetails()
                : null;
        return Result.control(next, actions, new ControlDecision(backoff,
                profileRedirect ? response.getRedirectUrl() : null, error),
                response.getStatePatch());
    }

    @Nonnull
    private SabrProfileRule selectRule(@Nonnull final ControlResponseEvent control,
                                       @Nonnull final SabrProfileMappedResponse response)
            throws SabrProtocolException {
        for (final SabrProfileRule rule : client.getRules()) {
            boolean matches = true;
            for (final SabrProfileRule.Predicate predicate : rule.getPredicates()) {
                if (!matches(predicate, control, response)) {
                    matches = false;
                    break;
                }
            }
            if (matches) return rule;
        }
        throw new SabrProtocolException("SABR profile has no matching behavior rule");
    }

    private static boolean matches(@Nonnull final SabrProfileRule.Predicate predicate,
                                   @Nonnull final ControlResponseEvent control,
                                   @Nonnull final SabrProfileMappedResponse response) {
        switch (predicate) {
            case HAS_MEDIA: return control.getResponse().hasMedia();
            case HAS_REDIRECT:
                return response.getRedirectUrl() != null && !response.getRedirectUrl().isEmpty();
            case HAS_PROTECTION_BOUNDARY: return response.getProtectionStatus() >= 2;
            case DEMANDED_SEGMENT_MISSING:
                return control.getMode() == ControlMode.FETCH_SEGMENT
                        && control.getSegmentCount() == 0;
            case BACKOFF_PRESENT: return response.getBackoffMs() > 0;
            case HAS_ERROR: return response.getErrorDetails() != null;
            case RELOAD_REQUESTED: return response.isReloadRequested();
            case PUMP_MODE: return control.getMode() == ControlMode.PUMP;
            case FETCH_SEGMENT_MODE: return control.getMode() == ControlMode.FETCH_SEGMENT;
            default: return false;
        }
    }

    @Nonnull
    private static ActionType actionType(@Nonnull final SabrProfileRule.Action action) {
        switch (action) {
            case CONTINUE: return ActionType.CONTINUE;
            case RETRY: return ActionType.RETRY;
            case APPLY_REDIRECT: return ActionType.APPLY_REDIRECT;
            case REFRESH_PO_TOKEN: return ActionType.REFRESH_PO_TOKEN;
            case REQUIRE_PO_TOKEN: return ActionType.REQUIRE_PO_TOKEN;
            case FAIL_SESSION: return ActionType.FAIL_SABR_ERROR;
            case TRY_RELOAD: return ActionType.TRY_RELOAD;
            default: throw new IllegalArgumentException("Unsupported SABR profile action");
        }
    }

    private static boolean isTerminal(@Nonnull final ActionType action) {
        return action == ActionType.CONTINUE || action == ActionType.RETRY
                || action == ActionType.FAIL_SABR_ERROR || action == ActionType.TRY_RELOAD;
    }

    @Nonnull
    @Override
    public DemandRoute evaluateDemandRoute(@Nonnull final DemandRouteEvent event) {
        final SabrProfileRecovery recovery = client.getRecovery();
        final DemandState demand = event.getState();
        final boolean recovering = demand.getResponsesWithoutDemandedSegment()
                > demand.getRecoveryCount();
        if (event.getTargetStartMs() < event.getBufferedEdgeMs()) {
            return recovering ? DemandRoute.RECOVER_REWIND : DemandRoute.REWIND;
        }
        if (event.getTargetStartMs()
                > event.getBufferedEdgeMs() + recovery.getForwardThresholdMs()) {
            return recovering ? DemandRoute.RECOVER_FORWARD : DemandRoute.FORWARD;
        }
        return recovering ? DemandRoute.RECOVER_MISSING : DemandRoute.STREAM;
    }

    @Nonnull
    @Override
    public DemandResponseDecision evaluateDemandResponse(
            @Nonnull final DemandResponseEvent event) {
        final SabrProfileRecovery recovery = client.getRecovery();
        final DemandState demand = event.getState();
        if (demand.getResponsesWithoutDemandedSegment() >= recovery.getMaximumOmissions()) {
            return new DemandResponseDecision(DemandOutcome.FAIL_REPEATED_TARGET_OMISSION, 0);
        }
        if (demand.getElapsedMs() >= recovery.getMaximumElapsedMs()) {
            return new DemandResponseDecision(event.getTargetTrackSegmentCount() > 0
                    ? DemandOutcome.FAIL_REPEATED_TARGET_OMISSION
                    : DemandOutcome.FAIL_NO_TARGET_MEDIA, 0);
        }
        return new DemandResponseDecision(DemandOutcome.CONTINUE, recovery.getRetryDelayMs());
    }

    @Nonnull
    @Override
    public SabrMediaProtocol getMediaProtocol() {
        return mediaProtocol;
    }
}
