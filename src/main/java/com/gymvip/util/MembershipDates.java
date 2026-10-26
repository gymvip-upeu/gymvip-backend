package com.gymvip.util;

import java.time.*;

/** Shared S1 coverage convention. End is exclusive; all decisions use Lima dates. */
public final class MembershipDates {
    public static final ZoneId ZONE = ZoneId.of("America/Lima");
    private MembershipDates() {}
    public static LocalDate today(Clock clock) { return LocalDate.now(clock.withZone(ZONE)); }
    public static LocalDate start(LocalDate today, LocalDate previousEnd) {
        return previousEnd != null && previousEnd.isAfter(today) ? previousEnd : today;
    }
    public static LocalDate end(LocalDate start) { return start.plusDays(30); }
}
