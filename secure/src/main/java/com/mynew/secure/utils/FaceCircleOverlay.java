package com.mynew.secure.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.View;

public class FaceCircleOverlay extends View {

    private float radius = 300f; // default radius
    private Paint overlayPaint;
    private Paint clearPaint;

    public FaceCircleOverlay(Context context) {
        super(context);
        init();
    }

    public FaceCircleOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FaceCircleOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Paint to dim the outside
        overlayPaint = new Paint();
        overlayPaint.setColor(0xAA000000); // semi-transparent black

        // Paint to "clear" the inside circle
        clearPaint = new Paint();
        clearPaint.setAntiAlias(true);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public void setRadius(float r) {
        radius = r;
        invalidate();
    }

    public float getRadius() {
        return radius;
    }

    public float getCenterX() {
        return getWidth() / 2f;
    }

    public float getCenterY() {
        return getHeight() / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw the dimmed overlay
        canvas.drawRect(0, 0, getWidth(), getHeight(), overlayPaint);

        // "Cut out" the circle from center
        canvas.drawCircle(getCenterX(), getCenterY(), radius, clearPaint);
    }
}
