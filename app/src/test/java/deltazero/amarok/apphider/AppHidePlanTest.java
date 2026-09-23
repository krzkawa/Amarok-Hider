package deltazero.amarok.apphider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Set;

public class AppHidePlanTest {

    private static final Set<String> HIDE = Set.of("a.keyboard", "b.chat", "c.game");

    @Test
    public void iconOnlyTakesTheMarkedAppsThatAreHidden() {
        // "d.other" is marked but no longer on the hidden list, so it is left alone.
        assertEquals(Set.of("a.keyboard"),
                AppHidePlan.iconOnly(HIDE, Set.of("a.keyboard", "d.other"), true));
    }

    @Test
    public void iconOnlyFallsBackToFullHidingWithoutComponentSupport() {
        Set<String> iconOnly = AppHidePlan.iconOnly(HIDE, Set.of("a.keyboard"), false);

        assertTrue(iconOnly.isEmpty());
        assertEquals(HIDE, AppHidePlan.fully(HIDE, iconOnly));
    }

    @Test
    public void fullyIsEverythingElse() {
        assertEquals(Set.of("b.chat", "c.game"), AppHidePlan.fully(HIDE, Set.of("a.keyboard")));
    }

    @Test
    public void leaveDisabledJoinsTheUsersChoiceAndThePriorState() {
        assertEquals(Set.of("b.chat", "c.game"),
                AppHidePlan.leaveDisabled(HIDE, Set.of("b.chat"), Set.of("c.game")));
    }

    @Test
    public void leaveDisabledIgnoresAppsNotBeingUnhidden() {
        assertTrue(AppHidePlan.leaveDisabled(Set.of("b.chat"), Set.of("x.gone"), Set.of("y.gone")).isEmpty());
    }

    @Test
    public void packagesOfReadsThePartBeforeTheSlash() {
        assertEquals(Set.of("a.keyboard", "e.app"), AppHidePlan.packagesOf(Set.of(
                "a.keyboard/a.keyboard.Main",
                "a.keyboard/a.keyboard.Settings$Alias",
                "e.app/.Launcher",
                "malformed")));
    }
}
