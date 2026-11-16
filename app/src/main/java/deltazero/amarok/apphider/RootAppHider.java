package deltazero.amarok.apphider;

import android.content.Context;

import com.topjohnwu.superuser.Shell;

import java.util.Set;

import deltazero.amarok.R;

public class RootAppHider extends BaseAppHider {

    public RootAppHider(Context context) {
        super(context);
    }

    @Override
    public void hide(Set<String> pkgNames, boolean disableOnly) {
        for (String p : pkgNames) {
            if (disableOnly) {
                // Only disable, skip hide step
                Shell.cmd(String.format("pm disable %s", p)).submit();
            } else {
                // Normal behavior: both disable and hide
                Shell.cmd(String.format("pm disable %s & pm hide %s", p, p)).submit();
            }
        }
    }

    @Override
    public void unhide(Set<String> pkgNames, Set<String> leaveDisabled) {
        for (String p : pkgNames) {
            if (leaveDisabled.contains(p))
                Shell.cmd(String.format("pm unhide %s", p)).submit();
            else
                Shell.cmd(String.format("pm unhide %s & pm enable %s", p, p)).submit();
        }
    }

    @Override
    public boolean supportsComponentHiding() {
        return true;
    }

    @Override
    public void setComponentsEnabled(Set<String> components, boolean enabled) {
        for (String c : components) {
            // Quoted, since nested class names carry a '$'.
            Shell.cmd(String.format("pm %s '%s'", enabled ? "enable" : "disable", c)).submit();
        }
    }

    @Override
    public void tryToActivate(ActivationCallbackListener activationCallbackListener) {
        Shell.getShell(shell -> {
            if (shell.isRoot()) {
                activationCallbackListener.onActivateCallback(RootAppHider.class, true, 0);
            } else {
                activationCallbackListener.onActivateCallback(RootAppHider.class, false, R.string.root_not_ava);
            }
        });
    }

    @Override
    public String getName() {
        return "Root";
    }

}