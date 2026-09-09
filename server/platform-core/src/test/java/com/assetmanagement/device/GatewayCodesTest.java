package com.assetmanagement.device;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayCodesTest {

    @Test
    void startsAt0001WhenEmpty() {
        assertEquals("0001", GatewayCodes.nextNumeric(List.of()));
    }

    @Test
    void skipsTakenNumericCodesIncludingUnpadded() {
        assertEquals("0004", GatewayCodes.nextNumeric(List.of("0001", "2", "0003", "GW-f696ee5b7d63")));
    }

    @Test
    void fillsFirstGap() {
        assertEquals("0002", GatewayCodes.nextNumeric(List.of("0001", "0003", "0009")));
    }
}
