package deltazero.amarok.apphider;

import java.util.HashSet;
import java.util.Set;

/**
 * Works out which of the apps to hide get which treatment. Free of Android dependencies, so it is
 * covered by the JVM unit tests.
 */
public final class AppHidePlan {

    private AppHidePlan() {
    }

    /**
     * @param hideApps          Apps to hide.
     * @param iconOnlyApps      Apps the user asked to hide only the launcher icon of.
     * @param componentsSupported Whether the current mode can turn off single components.
     * @return The apps to hide only the launcher icon of. Without component support, none: those
     * apps are hidden in full instead, as they were before the option existed.
     */
    public static Set<String> iconOnly(Set<String> hideApps, Set<String> iconOnlyApps,
                                       boolean componentsSupported) {
        var result = new HashSet<String>();
        if (!componentsSupported)
            return result;
        for (String p : hideApps)
            if (iconOnlyApps.contains(p))
                result.add(p);
        return result;
    }

    /**
     * @return The apps to hide in full: those not hidden by their icon only.
     */
    public static Set<String> fully(Set<String> hideApps, Set<String> iconOnly) {
        var result = new HashSet<>(hideApps);
        result.removeAll(iconOnly);
        return result;
    }

    /**
     * @param unhideApps  Apps being unhidden.
     * @param keepDisabled Apps the user asked to stay disabled after unhiding.
     * @param wereDisabled Apps that were already disabled before they were hidden.
     * @return The apps to make visible again without enabling them.
     */
    public static Set<String> leaveDisabled(Set<String> unhideApps, Set<String> keepDisabled,
                                            Set<String> wereDisabled) {
        var result = new HashSet<String>();
        for (String p : unhideApps)
            if (keepDisabled.contains(p) || wereDisabled.contains(p))
                result.add(p);
        return result;
    }

    /**
     * @param components Components as "package/class".
     * @return The packages they belong to.
     */
    public static Set<String> packagesOf(Set<String> components) {
        var result = new HashSet<String>();
        for (String c : components) {
            int slash = c.indexOf('/');
            if (slash > 0)
                result.add(c.substring(0, slash));
        }
        return result;
    }
}
