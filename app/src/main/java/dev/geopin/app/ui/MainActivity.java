package dev.geopin.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import dev.geopin.app.ModuleStatus;
import dev.geopin.app.R;
import dev.geopin.app.store.Favorites;
import dev.geopin.app.store.PinStore;
import dev.geopin.core.ConfigCodec;
import dev.geopin.core.Coordinate;
import dev.geopin.core.PinConfig;

/** Layar utama: sakelar titik diam, input koordinat, peta pemilih, cakupan paket, dan favorit. */
public final class MainActivity extends Activity {
    private static final String DEFAULT_ALLOWLIST = "dev.geopin.probe";
    private static final double DEFAULT_LATITUDE = -6.175392;
    private static final double DEFAULT_LONGITUDE = 106.827153;

    private static final int INK = Color.rgb(17, 32, 24);
    private static final int MUTED = Color.rgb(96, 112, 102);
    private static final int ACCENT = Color.rgb(22, 163, 74);
    private static final int DANGER = Color.rgb(190, 60, 50);

    private PinStore store;
    private Favorites favorites;
    private PinConfig config;

    private TextView statusValue;
    private TextView moduleValue;
    private TextView messageView;
    private Button toggleButton;
    private EditText coordinateInput;
    private EditText altitudeInput;
    private EditText bearingInput;
    private EditText scopeInput;
    private LinearLayout favoritesList;
    private LinearLayout mapContainer;
    private PinMapView mapView;
    private Button mapButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = PinStore.open(this);
        favorites = new Favorites(this);
        config = store.loadConfig();
        if (!store.hasAllowlist()) {
            store.saveAllowlist(DEFAULT_ALLOWLIST);
        }
        setContentView(buildContent());
        renderState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderModuleState();
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = column();
        root.setPadding(dp(16), dp(20), dp(16), dp(28));
        root.setBackgroundColor(Color.rgb(246, 248, 246));
        scroll.addView(root, new ViewGroup.LayoutParams(MATCH_WIDTH, WRAP_CONTENT));

        TextView title = label(getString(R.string.app_name), 26f, INK, true);
        root.addView(title);

        TextView subtitle = label("Titik diam untuk aplikasi yang di-scope", 13f, MUTED, false);
        root.addView(subtitle);

        LinearLayout statusCard = card();
        statusCard.addView(label(getString(R.string.pin_status_title), 12f, MUTED, true));
        statusValue = label("", 20f, INK, true);
        statusCard.addView(statusValue);

        LinearLayout.LayoutParams moduleTitleParams = new LinearLayout.LayoutParams(MATCH_WIDTH, WRAP_CONTENT);
        moduleTitleParams.topMargin = dp(12);
        statusCard.addView(label(getString(R.string.module_status_title), 12f, MUTED, true), moduleTitleParams);
        moduleValue = label("", 14f, MUTED, false);
        statusCard.addView(moduleValue);
        root.addView(statusCard);

        messageView = label("", 12f, DANGER, false);
        messageView.setVisibility(View.GONE);
        root.addView(messageView);

        toggleButton = primaryButton(getString(R.string.action_toggle_on));
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(MATCH_WIDTH, dp(52));
        toggleParams.topMargin = dp(16);
        root.addView(toggleButton, toggleParams);
        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onToggleClicked();
            }
        });

        LinearLayout coordinateCard = card();
        coordinateCard.addView(sectionTitle("Koordinat"));
        coordinateInput = field(getString(R.string.hint_coordinate), InputType.TYPE_CLASS_TEXT);
        coordinateCard.addView(coordinateInput);
        Button applyButton = secondaryButton(getString(R.string.action_apply_coordinate));
        coordinateCard.addView(applyButton);
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                applyCoordinate();
            }
        });

        LinearLayout numbersRow = row();
        altitudeInput = field(getString(R.string.label_altitude),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        bearingInput = field(getString(R.string.label_bearing),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        numbersRow.addView(altitudeInput, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        numbersRow.addView(new View(this), new LinearLayout.LayoutParams(dp(10), 1));
        numbersRow.addView(bearingInput, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        coordinateCard.addView(numbersRow);
        root.addView(coordinateCard);

        mapButton = secondaryButton(getString(R.string.action_open_map));
        LinearLayout.LayoutParams mapButtonParams = new LinearLayout.LayoutParams(MATCH_WIDTH, dp(46));
        mapButtonParams.topMargin = dp(8);
        root.addView(mapButton, mapButtonParams);
        mapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleMap();
            }
        });

        mapContainer = column();
        mapContainer.setVisibility(View.GONE);
        mapView = new PinMapView(this);
        mapView.setListener(new PinMapView.Listener() {
            @Override
            public void onPinChanged(double latitude, double longitude) {
                coordinateInput.setText(new Coordinate(latitude, longitude).format());
            }
        });
        mapContainer.addView(mapView, new LinearLayout.LayoutParams(MATCH_WIDTH, dp(320)));
        TextView mapHint = label(getString(R.string.map_hint), 11f, MUTED, false);
        mapHint.setPadding(0, dp(6), 0, 0);
        mapContainer.addView(mapHint);
        root.addView(mapContainer);

        Button favoriteButton = secondaryButton(getString(R.string.action_save_favorite));
        LinearLayout.LayoutParams favoriteParams = new LinearLayout.LayoutParams(MATCH_WIDTH, dp(46));
        favoriteParams.topMargin = dp(8);
        root.addView(favoriteButton, favoriteParams);
        favoriteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                promptSaveFavorite();
            }
        });

        LinearLayout scopeCard = card();
        scopeCard.addView(sectionTitle(getString(R.string.label_scope)));
        scopeInput = field(getString(R.string.hint_scope), InputType.TYPE_CLASS_TEXT);
        scopeCard.addView(scopeInput);
        TextView scopeNote = label(getString(R.string.scope_hint_note), 11f, MUTED, false);
        scopeNote.setPadding(0, dp(6), 0, 0);
        scopeCard.addView(scopeNote);
        Button scopeButton = secondaryButton(getString(R.string.action_save_scope));
        scopeCard.addView(scopeButton);
        scopeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                store.saveAllowlist(scopeInput.getText().toString());
                toast(getString(R.string.toast_scope_saved));
            }
        });
        LinearLayout.LayoutParams scopeParams = new LinearLayout.LayoutParams(MATCH_WIDTH, WRAP_CONTENT);
        scopeParams.topMargin = dp(16);
        root.addView(scopeCard, scopeParams);

        LinearLayout favoritesCard = card();
        favoritesCard.addView(sectionTitle(getString(R.string.favorites_title)));
        favoritesList = column();
        favoritesCard.addView(favoritesList);
        LinearLayout.LayoutParams favoritesParams = new LinearLayout.LayoutParams(MATCH_WIDTH, WRAP_CONTENT);
        favoritesParams.topMargin = dp(16);
        root.addView(favoritesCard, favoritesParams);

        return scroll;
    }

    private void renderState() {
        if (config != null) {
            coordinateInput.setText(config.center().format());
            Double manualAltitude = config.manualAltitudeMeters();
            altitudeInput.setText(manualAltitude == null ? "" : trimNumber(manualAltitude));
            bearingInput.setText(trimNumber(config.bearingDegrees()));
        } else {
            coordinateInput.setText(new Coordinate(DEFAULT_LATITUDE, DEFAULT_LONGITUDE).format());
            bearingInput.setText("0");
        }
        scopeInput.setText(store.loadAllowlist());
        renderPinState();
        renderModuleState();
        renderFavorites();
    }

    private void renderPinState() {
        boolean active = config != null && config.enabled();
        statusValue.setText(active ? R.string.status_pin_on : R.string.status_pin_off);
        statusValue.setTextColor(active ? ACCENT : MUTED);
        toggleButton.setText(active ? R.string.action_toggle_off : R.string.action_toggle_on);
        toggleButton.setBackground(rounded(active ? Color.rgb(110, 24, 20) : ACCENT, 14));
    }

    private void renderModuleState() {
        boolean active = ModuleStatus.isActive();
        moduleValue.setText(active ? R.string.status_module_active : R.string.status_module_missing);
        moduleValue.setTextColor(active ? ACCENT : DANGER);
        if (messageView != null) {
            messageView.setText(R.string.warning_module_inactive);
            messageView.setVisibility(active ? View.GONE : View.VISIBLE);
        }
    }

    private void onToggleClicked() {
        if (config != null && config.enabled()) {
            persist(config.withEnabled(false));
            renderState();
            return;
        }
        PinConfig parsed = parseInputs();
        if (parsed == null) {
            toast(getString(R.string.toast_invalid_input));
            return;
        }
        String label = config == null ? "" : config.label();
        persist(parsed.withLabel(label).withEnabled(true));
        renderState();
    }

    private void applyCoordinate() {
        PinConfig parsed = parseInputs();
        if (parsed == null) {
            toast(getString(R.string.toast_invalid_input));
            return;
        }
        boolean keepEnabled = config != null && config.enabled();
        String label = config == null ? "" : config.label();
        config = parsed.withLabel(label).withEnabled(keepEnabled);
        persist(config);
        renderState();
        toast(getString(R.string.toast_config_saved));
    }

    /** Membaca input apa adanya; tidak menampilkan pesan, tidak pernah melempar. */
    private PinConfig parseInputs() {
        Coordinate center;
        try {
            center = Coordinate.parse(coordinateInput.getText().toString());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        Double altitude = null;
        String altitudeText = altitudeInput.getText().toString().trim();
        if (!altitudeText.isEmpty()) {
            try {
                altitude = Double.valueOf(altitudeText);
            } catch (NumberFormatException broken) {
                return null;
            }
            if (altitude.isNaN() || altitude.isInfinite()) {
                return null;
            }
        }
        double bearing = 0.0;
        String bearingText = bearingInput.getText().toString().trim();
        if (!bearingText.isEmpty()) {
            try {
                bearing = Double.parseDouble(bearingText);
            } catch (NumberFormatException broken) {
                return null;
            }
        }
        try {
            return new PinConfig(center, altitude, bearing, false, "");
        } catch (IllegalArgumentException rejected) {
            return null;
        }
    }

    private void persist(PinConfig updated) {
        config = updated;
        store.saveConfig(updated, ConfigCodec.encode(updated));
    }

    private void toggleMap() {
        boolean showing = mapContainer.getVisibility() == View.VISIBLE;
        if (showing) {
            mapContainer.setVisibility(View.GONE);
            mapButton.setText(R.string.action_open_map);
            return;
        }
        if (!mapView.hasPin()) {
            double latitude = config != null ? config.center().latitude : DEFAULT_LATITUDE;
            double longitude = config != null ? config.center().longitude : DEFAULT_LONGITUDE;
            mapView.centerOn(latitude, longitude, 16.0);
            mapView.setPin(latitude, longitude);
        }
        mapContainer.setVisibility(View.VISIBLE);
        mapButton.setText(R.string.action_close_map);
    }

    private void promptSaveFavorite() {
        final PinConfig parsed = parseInputs();
        if (parsed == null) {
            toast(getString(R.string.toast_no_coordinate));
            return;
        }
        final EditText nameInput = field(getString(R.string.dialog_favorite_name), InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_save_favorite)
                .setView(nameInput)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = nameInput.getText().toString().trim();
                        List<PinConfig> updated = new ArrayList<>(favorites.load());
                        updated.add(parsed.withLabel(name));
                        favorites.save(updated);
                        renderFavorites();
                        toast(getString(R.string.toast_favorite_saved));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void renderFavorites() {
        favoritesList.removeAllViews();
        List<PinConfig> items = favorites.load();
        if (items.isEmpty()) {
            favoritesList.addView(label(getString(R.string.favorites_empty), 13f, MUTED, false));
            return;
        }
        for (int index = 0; index < items.size(); index++) {
            final int position = index;
            final PinConfig item = items.get(index);
            TextView row = label(Favorites.describe(item) + "\n" + item.center().format(), 14f, INK, false);
            row.setPadding(0, dp(10), 0, dp(10));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    applyFavorite(item);
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    confirmDelete(position);
                    return true;
                }
            });
            favoritesList.addView(row);
        }
    }

    private void applyFavorite(PinConfig item) {
        boolean keepEnabled = config != null && config.enabled();
        config = item.withEnabled(keepEnabled);
        persist(config);
        renderState();
    }

    private void confirmDelete(final int position) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_title)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        List<PinConfig> items = new ArrayList<>(favorites.load());
                        if (position >= 0 && position < items.size()) {
                            items.remove(position);
                            favorites.save(items);
                            renderFavorites();
                            toast(getString(R.string.toast_favorite_deleted));
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String trimNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout card() {
        LinearLayout layout = column();
        GradientDrawable background = rounded(Color.WHITE, 16);
        background.setStroke(dp(1), Color.rgb(226, 232, 226));
        layout.setBackground(background);
        int padding = dp(14);
        layout.setPadding(padding, padding, padding, padding);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_WIDTH, WRAP_CONTENT);
        params.topMargin = dp(14);
        layout.setLayoutParams(params);
        return layout;
    }

    private TextView label(String text, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }

    private TextView sectionTitle(String text) {
        TextView view = label(text, 13f, MUTED, true);
        view.setPadding(0, 0, 0, dp(6));
        return view;
    }

    private EditText field(String hint, int inputType) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setInputType(inputType);
        edit.setTextSize(14f);
        edit.setSingleLine(true);
        edit.setTextColor(INK);
        edit.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable background = rounded(Color.rgb(242, 245, 242), 10);
        background.setStroke(dp(1), Color.rgb(214, 222, 214));
        edit.setBackground(background);
        return edit;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15f);
        button.setBackground(rounded(ACCENT, 14));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(ACCENT);
        button.setTextSize(14f);
        GradientDrawable background = rounded(Color.rgb(236, 246, 238), 12);
        background.setStroke(dp(1), Color.rgb(190, 224, 198));
        button.setBackground(background);
        return button;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static final int MATCH_WIDTH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;
}
