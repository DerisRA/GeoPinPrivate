package dev.geopin.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewParent;

import dev.geopin.core.Mercator;

/**
 * Peta ubin ringan berbasis Web Mercator. Cukup untuk memilih satu titik diam:
 * geser untuk memindahkan peta, cubit atau ketuk dua kali untuk zoom, ketuk sekali untuk menaruh pin.
 */
public final class PinMapView extends View {
    public interface Listener {
        void onPinChanged(double latitude, double longitude);
    }

    private static final int TILE_SIZE = 256;
    private static final double MIN_ZOOM = 3.0;
    private static final double MAX_ZOOM = 19.0;

    private final TileStore tiles;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint placeholderPaint = new Paint();
    private final Paint markerFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint attributionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect tileRect = new Rect();
    private final Rect sourceRect = new Rect();
    private final GestureDetector gestures;
    private final ScaleGestureDetector scaler;

    private double centerLatitude = -6.2;
    private double centerLongitude = 106.8;
    private double zoom = 15.0;
    private double pinLatitude = Double.NaN;
    private double pinLongitude = Double.NaN;
    private Listener listener;
    private boolean pinching;

    public PinMapView(Context context) {
        this(context, null);
    }

    public PinMapView(Context context, AttributeSet attributes) {
        super(context, attributes);
        this.tiles = new TileStore(context);
        float density = getResources().getDisplayMetrics().density;
        placeholderPaint.setColor(Color.rgb(232, 236, 232));
        markerFill.setColor(Color.rgb(22, 163, 74));
        markerStroke.setColor(Color.WHITE);
        markerStroke.setStyle(Paint.Style.STROKE);
        markerStroke.setStrokeWidth(3f * density);
        attributionPaint.setColor(Color.rgb(60, 60, 60));
        attributionPaint.setTextSize(10f * density);
        attributionPaint.setShadowLayer(2f, 0f, 0f, Color.WHITE);
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent start, MotionEvent current, float distanceX, float distanceY) {
                pan(distanceX, distanceY);
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent event) {
                if (pinching) {
                    // akhir dari cubit: jangan anggap sebagai ketukan yang menaruh pin
                    pinching = false;
                    return true;
                }
                placePinAt(event.getX(), event.getY());
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent event) {
                zoom = Math.min(MAX_ZOOM, zoom + 1.0);
                invalidate();
                return true;
            }
        });
        scaler = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                pinching = true;
                zoom = Mercator.clampZoom(zoom + Math.log(detector.getScaleFactor()) / Math.log(2.0),
                        MIN_ZOOM, MAX_ZOOM);
                invalidate();
                return true;
            }
        });
        setFocusable(true);
        setClickable(true);
    }

    public void setListener(Listener newListener) {
        this.listener = newListener;
    }

    public void centerOn(double latitude, double longitude, double newZoom) {
        this.centerLatitude = Mercator.clampLatitude(latitude);
        this.centerLongitude = Mercator.normalizeLongitude(longitude);
        this.zoom = Mercator.clampZoom(newZoom, MIN_ZOOM, MAX_ZOOM);
        invalidate();
    }

    public void setPin(double latitude, double longitude) {
        this.pinLatitude = Mercator.clampLatitude(latitude);
        this.pinLongitude = Mercator.normalizeLongitude(longitude);
        invalidate();
    }

    public boolean hasPin() {
        return !Double.isNaN(pinLatitude) && !Double.isNaN(pinLongitude);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaler.onTouchEvent(event);
        if (!pinching && !scaler.isInProgress()) {
            gestures.onTouchEvent(event);
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            // peta menggenggam gestur ini: ScrollView induk tidak boleh mencuri drag vertikal
            requestParentIntercept(false);
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            requestParentIntercept(true);
            pinching = false;
        }
        return true;
    }

    private void requestParentIntercept(boolean allow) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(!allow);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        tiles.shutdown();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (tiles != null && visibility != VISIBLE) tiles.setViewport(0, 0, -1, 0, -1);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        double worldSize = worldSize();
        double centerX = Mercator.longitudeToX(centerLongitude, worldSize);
        double centerY = Mercator.latitudeToY(centerLatitude, worldSize);
        double left = centerX - getWidth() / 2.0;
        double top = centerY - getHeight() / 2.0;

        int zoomLevel = Mercator.tileZoom(zoom);
        double tileSize = Mercator.tileScreenSize(zoom);
        int tileCount = 1 << zoomLevel;
        int firstX = (int) Math.floor(left / tileSize);
        int lastX = (int) Math.ceil((left + getWidth()) / tileSize) - 1;
        int firstY = (int) Math.floor(top / tileSize);
        int lastY = (int) Math.ceil((top + getHeight()) / tileSize) - 1;
        tiles.setViewport(zoomLevel, firstX, lastX, firstY, lastY);

        for (int tileX = firstX; tileX <= lastX; tileX++) {
            for (int tileY = firstY; tileY <= lastY; tileY++) {
                if (tileY < 0 || tileY >= tileCount) {
                    continue;
                }
                final int wrappedX = ((tileX % tileCount) + tileCount) % tileCount;
                final int screenLeft = (int) Math.round(tileX * tileSize - left);
                final int screenTop = (int) Math.round(tileY * tileSize - top);
                tileRect.set(screenLeft, screenTop,
                        (int) Math.round((tileX + 1) * tileSize - left),
                        (int) Math.round((tileY + 1) * tileSize - top));
                Bitmap tile = tiles.peek(zoomLevel, wrappedX, tileY);
                if (tile != null) {
                    canvas.drawBitmap(tile, (Rect) null, tileRect, bitmapPaint);
                } else {
                    if (!drawCachedParent(canvas, zoomLevel, wrappedX, tileY)) {
                        canvas.drawRect(tileRect, placeholderPaint);
                    }
                    tiles.request(zoomLevel, wrappedX, tileY, new Runnable() {
                        @Override
                        public void run() {
                            postInvalidate();
                        }
                    });
                }
            }
        }

        if (hasPin()) {
            float pinX = (float) (Mercator.longitudeToX(pinLongitude, worldSize) - left);
            float pinY = (float) (Mercator.latitudeToY(pinLatitude, worldSize) - top);
            float radius = 8f * getResources().getDisplayMetrics().density;
            canvas.drawCircle(pinX, pinY, radius, markerFill);
            canvas.drawCircle(pinX, pinY, radius, markerStroke);
        }

        String attribution = "\u00a9 OpenStreetMap contributors";
        float padding = 6f * getResources().getDisplayMetrics().density;
        canvas.drawText(attribution, padding, getHeight() - padding, attributionPaint);
    }

    private boolean drawCachedParent(Canvas canvas, int level, int x, int y) {
        for (int delta = 1; delta <= Math.min(level, 8); delta++) {
            Bitmap parent = tiles.peek(level - delta, x >> delta, y >> delta);
            if (parent == null) continue;
            int count = 1 << delta;
            int size = TILE_SIZE / count;
            int left = (x & (count - 1)) * size;
            int top = (y & (count - 1)) * size;
            sourceRect.set(left, top, left + size, top + size);
            canvas.drawBitmap(parent, sourceRect, tileRect, bitmapPaint);
            return true;
        }
        return false;
    }

    private void pan(float distanceX, float distanceY) {
        double worldSize = worldSize();
        double centerX = Mercator.longitudeToX(centerLongitude, worldSize) - distanceX;
        double centerY = Mercator.latitudeToY(centerLatitude, worldSize) - distanceY;
        centerLongitude = Mercator.normalizeLongitude(Mercator.xToLongitude(centerX, worldSize));
        centerLatitude = Mercator.clampLatitude(Mercator.yToLatitude(centerY, worldSize));
        invalidate();
    }

    private void placePinAt(float screenX, float screenY) {
        double worldSize = worldSize();
        double left = Mercator.longitudeToX(centerLongitude, worldSize) - getWidth() / 2.0;
        double top = Mercator.latitudeToY(centerLatitude, worldSize) - getHeight() / 2.0;
        double latitude = Mercator.clampLatitude(Mercator.yToLatitude(top + screenY, worldSize));
        double longitude = Mercator.normalizeLongitude(Mercator.xToLongitude(left + screenX, worldSize));
        pinLatitude = latitude;
        pinLongitude = longitude;
        invalidate();
        if (listener != null) {
            listener.onPinChanged(latitude, longitude);
        }
    }

    private double worldSize() {
        return TILE_SIZE * Math.pow(2.0, zoom);
    }
}
