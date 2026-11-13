package dev.geopin.probe;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Aplikasi diagnostik: menampilkan lokasi yang benar-benar diterima proses aplikasi ini.
 * Memakai {@link LocationListener} legacy secara sengaja, karena justru jalur itulah yang diubah
 * modul pada Android 11.
 */
@SuppressWarnings("deprecation")
public final class ProbeActivity extends Activity {
    private static final int REQUEST_LOCATION = 42;
    private static final String[] PROVIDERS = {"gps", "network", "fused"};

    private static final int INK = Color.rgb(17, 24, 39);
    private static final int MUTED = Color.rgb(100, 116, 139);
    private static final int ACCENT = Color.rgb(37, 99, 235);

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);

    private LocationManager locationManager;
    private TextView readout;
    private Button startButton;
    private boolean listening;

    private final LocationListener listener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            render(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // status provider tidak dipakai; diagnostik fokus pada nilai Location
        }

        @Override
        public void onProviderEnabled(String provider) {
            // tidak ada aksi
        }

        @Override
        public void onProviderDisabled(String provider) {
            // tidak ada aksi
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        setContentView(buildContent());
    }

    @Override
    protected void onDestroy() {
        stopListening();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_LOCATION) {
            return;
        }
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            startListening();
        } else {
            readout.setText(R.string.permission_denied);
        }
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(24));
        root.setBackgroundColor(Color.rgb(246, 248, 251));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(24f);
        title.setTextColor(INK);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView note = new TextView(this);
        note.setText(R.string.note);
        note.setTextSize(12f);
        note.setTextColor(MUTED);
        note.setPadding(0, dp(6), 0, dp(14));
        root.addView(note);

        readout = new TextView(this);
        readout.setText(R.string.readout_idle);
        readout.setTextSize(13f);
        readout.setTextColor(INK);
        readout.setTypeface(android.graphics.Typeface.MONOSPACE);
        readout.setBackground(rounded(1));
        int padding = dp(14);
        readout.setPadding(padding, padding, padding, padding);
        root.addView(readout);

        startButton = new Button(this);
        startButton.setText(R.string.action_start);
        startButton.setAllCaps(false);
        startButton.setTextColor(Color.WHITE);
        startButton.setBackground(rounded(2));
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        startParams.topMargin = dp(14);
        root.addView(startButton, startParams);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listening) {
                    stopListening();
                } else if (ensurePermission()) {
                    startListening();
                }
            }
        });

        Button lastKnownButton = new Button(this);
        lastKnownButton.setText(R.string.action_last_known);
        lastKnownButton.setAllCaps(false);
        lastKnownButton.setTextColor(ACCENT);
        lastKnownButton.setBackground(rounded(3));
        LinearLayout.LayoutParams lastParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        lastParams.topMargin = dp(8);
        root.addView(lastKnownButton, lastParams);
        lastKnownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                readLastKnown();
            }
        });

        return scroll;
    }

    private boolean ensurePermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        return false;
    }

    private void startListening() {
        try {
            for (String provider : PROVIDERS) {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(provider, 1000L, 0f, listener);
                }
            }
            listening = true;
            startButton.setText(R.string.action_stop);
        } catch (SecurityException denied) {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show();
        } catch (IllegalArgumentException unsupported) {
            Toast.makeText(this, "Provider tidak didukung: " + unsupported.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopListening() {
        if (locationManager != null && listening) {
            try {
                locationManager.removeUpdates(listener);
            } catch (SecurityException ignored) {
                // izin dicabut saat aplikasi berjalan
            }
            listening = false;
        }
        if (startButton != null) {
            startButton.setText(R.string.action_start);
        }
    }

    private void readLastKnown() {
        if (!ensurePermission()) {
            return;
        }
        Location newest = null;
        for (String provider : PROVIDERS) {
            try {
                Location candidate = locationManager.getLastKnownLocation(provider);
                if (candidate != null && (newest == null || candidate.getTime() > newest.getTime())) {
                    newest = candidate;
                }
            } catch (SecurityException denied) {
                readout.setText(R.string.permission_denied);
                return;
            } catch (IllegalArgumentException unsupported) {
                // provider tidak ada di perangkat ini
            }
        }
        if (newest == null) {
            readout.setText("Tidak ada lokasi tersimpan untuk provider mana pun.");
            return;
        }
        render(newest);
    }

    private void render(Location location) {
        StringBuilder builder = new StringBuilder();
        builder.append("provider   : ").append(location.getProvider()).append('\n');
        builder.append(String.format(Locale.ROOT, "latitude   : %.7f%n", location.getLatitude()));
        builder.append(String.format(Locale.ROOT, "longitude  : %.7f%n", location.getLongitude()));
        builder.append(String.format(Locale.ROOT, "accuracy   : %.2f m%n", location.getAccuracy()));
        builder.append(String.format(Locale.ROOT, "altitude   : %.2f m%n", location.getAltitude()));
        builder.append(String.format(Locale.ROOT, "speed      : %.3f m/s%n", location.getSpeed()));
        builder.append(String.format(Locale.ROOT, "bearing    : %.1f deg%n", location.getBearing()));
        builder.append("mock       : ").append(location.isFromMockProvider()).append('\n');
        builder.append("time       : ").append(timeFormat.format(new Date(location.getTime()))).append('\n');
        builder.append("elapsed    : ").append(location.getElapsedRealtimeNanos()).append(" ns\n");
        builder.append("hasAcc/Alt : ").append(location.hasAccuracy()).append('/').append(location.hasAltitude());
        readout.setText(builder.toString());
    }

    private GradientDrawable rounded(int style) {
        GradientDrawable drawable = new GradientDrawable();
        if (style == 1) {
            drawable.setColor(Color.WHITE);
            drawable.setStroke(dp(1), Color.rgb(226, 232, 240));
        } else if (style == 2) {
            drawable.setColor(ACCENT);
        } else {
            drawable.setColor(Color.rgb(232, 240, 254));
            drawable.setStroke(dp(1), Color.rgb(191, 214, 250));
        }
        drawable.setCornerRadius(dp(14));
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
