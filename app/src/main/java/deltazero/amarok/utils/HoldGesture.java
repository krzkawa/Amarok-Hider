package deltazero.amarok.utils;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Runs an action once a view has been held down for a set time, which can be much longer than
 * the system's long press.
 */
public final class HoldGesture {

    private HoldGesture() {
    }

    /**
     * @param holdMillis How long to hold, or 0 for the system's long press.
     */
    @SuppressLint("ClickableViewAccessibility")
    public static void attach(View view, long holdMillis, Runnable onHold) {
        if (holdMillis <= 0) {
            view.setOnLongClickListener(v -> {
                onHold.run();
                return true;
            });
            return;
        }

        // A view that is not clickable gets no events after the first touch, so a release
        // could not cancel the hold.
        view.setClickable(true);

        int touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        Runnable fire = onHold::run;
        float[] down = new float[2];

        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    down[0] = event.getX();
                    down[1] = event.getY();
                    v.postDelayed(fire, holdMillis);
                }
                case MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(event.getX() - down[0]) > touchSlop
                            || Math.abs(event.getY() - down[1]) > touchSlop)
                        v.removeCallbacks(fire);
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.removeCallbacks(fire);
            }
            // Let taps through, so a held date can still be selected.
            return false;
        });
    }
}
