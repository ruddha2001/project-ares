package codes.ani.ares.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class AresStateTest {

    @Test
    void stateShouldMaintainImmutability() {
        AresState state = AresState.init("TEST-1", Map.of());

        assertEquals("TEST-1", state.getRequestId());
        assertEquals(AresState.VerificationMode.UNKNOWN, state.getMode());

        AresState evolved = new AresState(state.getRequestId(), "Requirement Text", state.getSanitizedRequirement(), AresState.VerificationMode.API, state.getAuditTrail(), state.getMetadata());

        assertNotSame(state, evolved);
        assertEquals("Requirement Text", evolved.getRawRequirements());
    }
}
