package com.gymvip;

import com.gymvip.util.MembershipDates;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class MembershipDatesTest {
    @Test void thirtyDaysAcrossLeapFebruaryAndYearBoundary() {
        assertEquals(LocalDate.of(2024, 3, 1), MembershipDates.end(LocalDate.of(2024, 1, 31)));
        assertEquals(LocalDate.of(2027, 1, 14), MembershipDates.end(LocalDate.of(2026, 12, 15)));
    }
    @Test void midnightUsesLimaAndEndIsExclusive() {
        var clock = Clock.fixed(Instant.parse("2026-10-27T04:59:59Z"), ZoneOffset.UTC);
        assertEquals(LocalDate.of(2026, 10, 26), MembershipDates.today(clock));
        assertEquals(LocalDate.of(2026, 10, 27), MembershipDates.today(Clock.offset(clock, Duration.ofSeconds(1))));
        LocalDate today = LocalDate.of(2026, 10, 26);
        assertEquals(today, MembershipDates.start(today, today));
        assertEquals(today, MembershipDates.start(today, today.minusDays(1)));
        assertEquals(today.plusDays(4), MembershipDates.start(today, today.plusDays(4)));
    }
}
