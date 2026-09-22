package deltazero.amarok.ui;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.button.MaterialButton;
import com.takusemba.spotlight.Spotlight;
import com.takusemba.spotlight.Target;
import com.takusemba.spotlight.shape.Circle;

import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;
import deltazero.amarok.utils.CalculatorEngine;
import deltazero.amarok.utils.CalculatorInput;
import deltazero.amarok.utils.SecurityUtil;

/**
 * Disguises Amarok as a calculator.
 * <p>
 * The keypad does real arithmetic, so the app holds up if someone else opens it. Amarok is
 * reached by typing the equation set in the settings and pressing '=', which is the calculator's
 * counterpart to long pressing the year in {@link CalendarActivity}.
 */
public class CalculatorActivity extends AppCompatActivity {

    private final CalculatorInput input = new CalculatorInput();

    private TextView tvExpression, tvPreview;
    private MaterialButton btEquals;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0);
        else
            overridePendingTransition(0, 0);

        super.onCreate(savedInstanceState);

        // Enable edge-to-edge (CalculatorActivity doesn't extend AmarokActivity)
        WindowCompat.enableEdgeToEdge(getWindow());

        setContentView(R.layout.activity_calculator);

        tvExpression = findViewById(R.id.calculator_tv_expression);
        tvPreview = findViewById(R.id.calculator_tv_preview);
        btEquals = findViewById(R.id.calculator_bt_equals);

        setupKeypad();
        updateDisplay();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
            }
        });

        if (PrefMgr.getDoShowQuitDisguiseInstuct()) {
            showInstruction();
        }
    }

    private void setupKeypad() {

        int[] digitIds = {
                R.id.calculator_bt_0, R.id.calculator_bt_1, R.id.calculator_bt_2,
                R.id.calculator_bt_3, R.id.calculator_bt_4, R.id.calculator_bt_5,
                R.id.calculator_bt_6, R.id.calculator_bt_7, R.id.calculator_bt_8,
                R.id.calculator_bt_9
        };

        for (int digit = 0; digit < digitIds.length; digit++) {
            char c = (char) ('0' + digit);
            findViewById(digitIds[digit]).setOnClickListener(v -> onEdit(() -> input.appendDigit(c)));
        }

        findViewById(R.id.calculator_bt_decimal)
                .setOnClickListener(v -> onEdit(input::appendDecimalPoint));
        findViewById(R.id.calculator_bt_add)
                .setOnClickListener(v -> onEdit(() -> input.appendOperator('+')));
        findViewById(R.id.calculator_bt_subtract)
                .setOnClickListener(v -> onEdit(() -> input.appendOperator(CalculatorEngine.DISPLAY_MINUS)));
        findViewById(R.id.calculator_bt_multiply)
                .setOnClickListener(v -> onEdit(() -> input.appendOperator(CalculatorEngine.DISPLAY_MULTIPLY)));
        findViewById(R.id.calculator_bt_divide)
                .setOnClickListener(v -> onEdit(() -> input.appendOperator(CalculatorEngine.DISPLAY_DIVIDE)));
        findViewById(R.id.calculator_bt_parenthesis)
                .setOnClickListener(v -> onEdit(input::appendParenthesis));
        findViewById(R.id.calculator_bt_sign)
                .setOnClickListener(v -> onEdit(input::toggleSign));
        findViewById(R.id.calculator_bt_backspace)
                .setOnClickListener(v -> onEdit(input::backspace));
        findViewById(R.id.calculator_bt_clear)
                .setOnClickListener(v -> onEdit(input::clear));

        findViewById(R.id.calculator_bt_backspace).setOnLongClickListener(v -> {
            onEdit(input::clear);
            return true;
        });

        btEquals.setOnClickListener(v -> onEquals());
    }

    private void onEdit(Runnable edit) {
        edit.run();
        updateDisplay();
    }

    /**
     * Either open Amarok, when the display holds the unlock equation, or work out the answer.
     */
    private void onEquals() {

        if (CalculatorEngine.matchesUnlockEquation(input.getExpression(),
                PrefMgr.getCalculatorUnlockEquation())) {
            SecurityUtil.dismissDisguise();
            finish();
            return;
        }

        String result = evaluateOrNull();
        if (result == null)
            return;

        input.set(result);
        updateDisplay();
    }

    private void updateDisplay() {
        tvExpression.setText(input.isEmpty() ? "0" : input.getExpression());

        String preview = evaluateOrNull();
        // Showing the answer twice is noise, so the preview only appears while it adds something.
        tvPreview.setText(preview == null || preview.equals(input.getExpression()) ? "" : preview);
    }

    /**
     * @return The value of what is on the display, or null if it is not something to work out yet.
     */
    @Nullable
    private String evaluateOrNull() {
        try {
            return CalculatorEngine.evaluateToString(input.getExpression());
        } catch (CalculatorEngine.ExpressionException e) {
            return null;
        }
    }

    @SuppressLint("MissingInflatedId")
    private void showInstruction() {

        float circleRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100, getResources().getDisplayMetrics());

        var spotlightLayout = getLayoutInflater().inflate(R.layout.spotlight_layout, new FrameLayout(this));
        // expandTemplate rather than getString, so the tip keeps its highlighting.
        ((TextView) spotlightLayout.findViewById(R.id.calendar_tv_spotlight_tip)).setText(
                TextUtils.expandTemplate(getText(R.string.close_calculator_disguise_spotlight_tip),
                        PrefMgr.getCalculatorUnlockEquation()));

        var target = new Target.Builder()
                .setAnchor(btEquals)
                .setShape(new Circle(circleRadius))
                .setOverlay(spotlightLayout)
                .build();

        var spotlight = new Spotlight.Builder(this)
                .setTargets(target)
                .setBackgroundColor(getColor(R.color.dark_grey))
                .setDuration(800L)
                .build();

        spotlightLayout.setOnClickListener(v -> spotlight.finish());
        spotlightLayout.findViewById(R.id.calendar_bt_spotlight_do_not_show_again).setOnClickListener(v -> {
            spotlight.finish();
            PrefMgr.setDoShowQuitDisguiseInstuct(false);
        });
        spotlight.start();
    }
}
