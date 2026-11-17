package at.esoren.vystormgallery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProtectionProbeTest {
    private final ProtectionProbe.Sealer sealer = new ProtectionProbe.Sealer();

    @Test
    void anAllowedProbeIsCancelledForEveryoneAfterTheSeal() {
        ProtectionProbe probe = new ProtectionProbe(null, null);
        sealer.onProbe(probe);
        assertTrue(probe.allowed());
        assertTrue(probe.isCancelled(), "loggers and other MONITOR listeners skip it");
        probe.setCancelled(false);
        assertTrue(probe.isCancelled(), "nobody can turn it back into a real click");
        assertTrue(probe.allowed());
    }

    @Test
    void aDenialBeforeOrAfterTheSealCounts() {
        ProtectionProbe before = new ProtectionProbe(null, null);
        before.setCancelled(true);
        sealer.onProbe(before);
        assertFalse(before.allowed());

        ProtectionProbe after = new ProtectionProbe(null, null);
        sealer.onProbe(after);
        after.setCancelled(true);
        assertFalse(after.allowed());
        after.setCancelled(false);
        assertFalse(after.allowed(), "a later un-cancel does not undo a denial");
    }

    @Test
    void withoutTheSealTheCancelStateIsTheAnswer() {
        ProtectionProbe probe = new ProtectionProbe(null, null);
        assertTrue(probe.allowed());
        probe.setCancelled(true);
        assertFalse(probe.allowed());
    }
}
