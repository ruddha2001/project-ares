package codes.ani.ares.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class AresStateTest {

    @Test
    void stateShouldMaintainImmutability() {
        AresState state = AresState.init("TEST-1");

        assertEquals("TEST-1", state.requestId());
        assertEquals(AresState.VerificationMode.UNKNOWN, state.mode());

        AresState evolved = new AresState(state.requestId(), "Requirement Text", state.sanitizedRequirement(), AresState.VerificationMode.API, state.auditTrail(), state.metadata());

        assertNotSame(state, evolved);
        assertEquals("Requirement Text", evolved.rawRequirements());
    }
}
