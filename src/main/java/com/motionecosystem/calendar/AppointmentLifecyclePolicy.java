package com.motionecosystem.calendar;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** The single source of truth for appointment lifecycle decisions. */
public final class AppointmentLifecyclePolicy {
    public enum Action { UPDATE, CANCEL, COMPLETE, MARK_NO_SHOW }

    public boolean allows(Action action, Appointment appointment, Instant now) {
        if (appointment == null || now == null) return false;
        return switch (action) {
            case UPDATE, CANCEL -> schedulable(appointment.status) && now.isBefore(appointment.startsAt);
            case COMPLETE -> (schedulable(appointment.status) && !now.isBefore(appointment.startsAt))
                    || appointment.status == Appointment.Status.IN_PROGRESS;
            case MARK_NO_SHOW -> schedulable(appointment.status) && !now.isBefore(appointment.endsAt);
        };
    }

    public boolean isEditable(Appointment appointment, Instant now) {
        return allows(Action.UPDATE, appointment, now);
    }

    public List<String> availableActions(Appointment appointment, Instant now) {
        List<String> actions = new ArrayList<>(List.of("OPEN_APPOINTMENT", "OPEN_PARTICIPANT"));
        for (Action action : Action.values()) {
            if (allows(action, appointment, now)) actions.add(actionName(action));
        }
        return List.copyOf(actions);
    }

    /** Statuses for appointments whose outcome can still be recorded after they have ended. */
    public EnumSet<Appointment.Status> outcomeOutstandingStatuses() {
        return EnumSet.of(Appointment.Status.SCHEDULED, Appointment.Status.CONFIRMED, Appointment.Status.IN_PROGRESS);
    }

    private static boolean schedulable(Appointment.Status status) {
        return status == Appointment.Status.SCHEDULED || status == Appointment.Status.CONFIRMED;
    }

    private static String actionName(Action action) {
        return switch (action) {
            case UPDATE -> "UPDATE";
            case CANCEL -> "CANCEL";
            case COMPLETE -> "COMPLETE";
            case MARK_NO_SHOW -> "MARK_NO_SHOW";
        };
    }
}
