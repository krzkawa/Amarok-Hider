package deltazero.amarok.apphider;

import android.content.Context;

import java.util.Set;

public abstract class BaseAppHider {
    public Context context;

    public BaseAppHider(Context context) {
        this.context = context;
    }

    /**
     * Hide apps with option to only disable them (skip the hide step)
     * @param pkgNames Package names to hide
     * @param disableOnly If true, only disable apps without hiding them from system
     */
    public abstract void hide(Set<String> pkgNames, boolean disableOnly);

    /**
     * Unhide apps, leaving some of them disabled.
     * @param pkgNames Package names to unhide
     * @param leaveDisabled Packages among them to make visible again without enabling them
     */
    public abstract void unhide(Set<String> pkgNames, Set<String> leaveDisabled);

    public void unhide(Set<String> pkgNames) {
        unhide(pkgNames, Set.of());
    }

    /**
     * Whether this mode can turn off single components, which is what hiding only the launcher
     * icon of an app takes.
     */
    public boolean supportsComponentHiding() {
        return false;
    }

    /**
     * Turn components on or off. Only called when {@link #supportsComponentHiding()} is true.
     * @param components Components as "package/class"
     * @param enabled Whether to turn them on
     */
    public void setComponentsEnabled(Set<String> components, boolean enabled) {
    }

    public abstract void tryToActivate(ActivationCallbackListener activationCallbackListener);

    public abstract String getName();

    public interface ActivationCallbackListener {
        void onActivateCallback(Class<? extends BaseAppHider> appHider, boolean success, int msgResID);
    }
}
