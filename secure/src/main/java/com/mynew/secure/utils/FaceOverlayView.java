package com.mynew.secure.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class FaceOverlayView extends View {

    public enum FaceStatus {
        NO_FACE,
        GOOD_POSITION,
        TOO_CLOSE,
        TOO_FAR,
        NOT_STRAIGHT,
        MULTIPLE_FACES
    }

    private Paint overlayPaint;
    private Paint guidePaint;
    private Paint strokePaint;
    private Paint textPaint;
    private RectF faceRect;
    private FaceStatus currentStatus = FaceStatus.NO_FACE;
    private RectF guideOval;
    private Path guidePath;

    // Animation variables
    private float animationProgress = 0f;
    private boolean isAnimating = false;

    // Colors for different states
    private static final int COLOR_GOOD = Color.parseColor("#4CAF50");  // Green
    private static final int COLOR_WARNING = Color.parseColor("#FF9800"); // Orange
    private static final int COLOR_ERROR = Color.parseColor("#F44336");   // Red
    private static final int COLOR_INFO = Color.parseColor("#2196F3");    // Blue
    private static final int OVERLAY_ALPHA = 100; // Semi-transparent overlay

    public FaceOverlayView(Context context) {
        super(context);
        init();
    }

    public FaceOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FaceOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Paint for the semi-transparent overlay
        overlayPaint = new Paint();
        overlayPaint.setColor(Color.BLACK);
        overlayPaint.setAlpha(OVERLAY_ALPHA);
        overlayPaint.setAntiAlias(true);

        // Paint for the guide oval (clear area)
        guidePaint = new Paint();
        guidePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        guidePaint.setAntiAlias(true);

        // Paint for the stroke around the guide
        strokePaint = new Paint();
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(8f);
        strokePaint.setAntiAlias(true);

        // Paint for instruction text
        textPaint = new Paint();
        textPaint.setTextSize(48f);
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        textPaint.setShadowLayer(4f, 0f, 2f, Color.BLACK);

        guidePath = new Path();

        // Initialize guide oval (will be set properly in onSizeChanged)
        guideOval = new RectF();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Calculate the guide oval position (centered on screen)
        float centerX = w / 2f;
        float centerY = h / 2f;

        // Make oval size responsive to screen size
        float ovalWidth = Math.min(w, h) * 0.80f;  // 65% of the smaller dimension
        float ovalHeight = ovalWidth * 1.4f;        // Slightly taller for face shape

        float left = centerX - ovalWidth / 2f;
        float top = centerY - ovalHeight / 2f;
        float right = centerX + ovalWidth / 2f;
        float bottom = centerY + ovalHeight / 2f;

        guideOval.set(left, top, right, bottom);
    }

    public void updateFacePosition(RectF detectedFaceRect, FaceStatus status) {
        this.faceRect = detectedFaceRect;
        this.currentStatus = status;

        // Update stroke color based on status
        updateStrokeColor();

        // Trigger animation for status changes
        startStatusAnimation();

        invalidate(); // Redraw the view
    }

    private void updateStrokeColor() {
        switch (currentStatus) {
            case GOOD_POSITION:
                strokePaint.setColor(COLOR_GOOD);
                break;
            case TOO_CLOSE:
            case TOO_FAR:
                strokePaint.setColor(COLOR_WARNING);
                break;
            case NOT_STRAIGHT:
            case MULTIPLE_FACES:
                strokePaint.setColor(COLOR_ERROR);
                break;
            case NO_FACE:
            default:
                strokePaint.setColor(COLOR_INFO);
                break;
        }
    }

    private void startStatusAnimation() {
        if (!isAnimating) {
            isAnimating = true;
            animationProgress = 0f;

            // Simple animation using post delayed
            animateStroke();
        }
    }

    private void animateStroke() {
        if (animationProgress < 1f) {
            animationProgress += 0.05f; // Increment animation

            // Update stroke width based on animation progress
            float baseStrokeWidth = 8f;
            float animatedStrokeWidth = baseStrokeWidth + (4f * (float)Math.sin(animationProgress * Math.PI * 4));
            strokePaint.setStrokeWidth(animatedStrokeWidth);

            invalidate();
            postDelayed(this::animateStroke, 16); // ~60 FPS
        } else {
            isAnimating = false;
            strokePaint.setStrokeWidth(8f); // Reset to normal width
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;
        // Save the canvas state
        int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        // Draw the semi-transparent overlay
        //canvas.drawRect(0, 0, getWidth(), getHeight(), overlayPaint);
        // Clear the oval area (create transparent hole)
        canvas.drawOval(guideOval, guidePaint);
        // Restore canvas state
        canvas.restoreToCount(saveCount);
        // Draw the guide stroke around the oval
        drawGuideStroke(canvas);
        // Draw corner guides for better UX
        //drawCornerGuides(canvas);
        // Draw face detection indicator if face is detected
        if (faceRect != null && currentStatus != FaceStatus.NO_FACE) {
        //    drawFaceIndicator(canvas);
        }
        // Draw status indicators
        drawStatusIndicators(canvas);
    }

    private void drawGuideStroke(Canvas canvas) {
        // Draw dashed oval stroke
        float dashWidth = 20f;
        float dashGap = 15f;

        // Calculate oval circumference and draw dashed line
        float circumference = (float) (2 * Math.PI * Math.sqrt((Math.pow(guideOval.width()/2, 2) + Math.pow(guideOval.height()/2, 2)) / 2));
        int numDashes = (int) (circumference / (dashWidth + dashGap));

        for (int i = 0; i < numDashes; i++) {
            float angle = (float) (2 * Math.PI * i / numDashes);
            float startAngle = (float) Math.toDegrees(angle);
            float sweepAngle = (float) Math.toDegrees(dashWidth / (circumference / (2 * Math.PI)));

            canvas.drawArc(guideOval, startAngle, sweepAngle, false, strokePaint);
        }
    }

    private void drawCornerGuides(Canvas canvas) {
        Paint cornerPaint = new Paint();
        cornerPaint.setColor(strokePaint.getColor());
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(6f);
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);
        cornerPaint.setAntiAlias(true);

        float cornerLength = 30f;
        float margin = 40f;

        // Top-left corner
        canvas.drawLine(margin, margin + cornerLength, margin, margin, cornerPaint);
        canvas.drawLine(margin, margin, margin + cornerLength, margin, cornerPaint);

        // Top-right corner
        canvas.drawLine(getWidth() - margin - cornerLength, margin, getWidth() - margin, margin, cornerPaint);
        canvas.drawLine(getWidth() - margin, margin, getWidth() - margin, margin + cornerLength, cornerPaint);

        // Bottom-left corner
        canvas.drawLine(margin, getHeight() - margin - cornerLength, margin, getHeight() - margin, cornerPaint);
        canvas.drawLine(margin, getHeight() - margin, margin + cornerLength, getHeight() - margin, cornerPaint);

        // Bottom-right corner
        canvas.drawLine(getWidth() - margin - cornerLength, getHeight() - margin, getWidth() - margin, getHeight() - margin, cornerPaint);
        canvas.drawLine(getWidth() - margin, getHeight() - margin, getWidth() - margin, getHeight() - margin - cornerLength, cornerPaint);
    }

    private void drawFaceIndicator(Canvas canvas) {
        if (faceRect == null) return;

        Paint facePaint = new Paint();
        facePaint.setStyle(Paint.Style.STROKE);
        facePaint.setStrokeWidth(4f);
        facePaint.setAntiAlias(true);
        facePaint.setColor(strokePaint.getColor());

        // Draw rectangle around detected face
        canvas.drawRect(faceRect, facePaint);

        // Draw small circles at face corners
        float radius = 8f;
        Paint circlePaint = new Paint();
        circlePaint.setColor(strokePaint.getColor());
        circlePaint.setAntiAlias(true);

        canvas.drawCircle(faceRect.left, faceRect.top, radius, circlePaint);
        canvas.drawCircle(faceRect.right, faceRect.top, radius, circlePaint);
        canvas.drawCircle(faceRect.left, faceRect.bottom, radius, circlePaint);
        canvas.drawCircle(faceRect.right, faceRect.bottom, radius, circlePaint);
    }

    private void drawStatusIndicators(Canvas canvas) {
        // Draw status icon in top center
        float iconY = 100f;
        float iconSize = 60f;

        Paint iconPaint = new Paint();
        iconPaint.setAntiAlias(true);
        iconPaint.setColor(strokePaint.getColor());
        iconPaint.setStyle(Paint.Style.FILL);

        float centerX = getWidth() / 2f;

        switch (currentStatus) {
            case GOOD_POSITION:
                // Draw checkmark
                drawCheckmark(canvas, centerX, iconY, iconSize, iconPaint);
                break;
            case TOO_CLOSE:
                // Draw minus sign
                drawMinus(canvas, centerX, iconY, iconSize, iconPaint);
                break;
            case TOO_FAR:
                // Draw plus sign
                drawPlus(canvas, centerX, iconY, iconSize, iconPaint);
                break;
            case NOT_STRAIGHT:
                // Draw rotation arrow
                drawRotationArrow(canvas, centerX, iconY, iconSize, iconPaint);
                break;
            case MULTIPLE_FACES:
                // Draw warning triangle
                drawWarning(canvas, centerX, iconY, iconSize, iconPaint);
                break;
            case NO_FACE:
                // Draw face outline
                drawFaceOutline(canvas, centerX, iconY, iconSize, iconPaint);
                break;
        }
    }

    private void drawCheckmark(Canvas canvas, float x, float y, float size, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8f);
        paint.setStrokeCap(Paint.Cap.ROUND);

        Path checkPath = new Path();
        checkPath.moveTo(x - size/3, y);
        checkPath.lineTo(x - size/6, y + size/3);
        checkPath.lineTo(x + size/3, y - size/3);

        canvas.drawPath(checkPath, paint);
    }

    private void drawMinus(Canvas canvas, float x, float y, float size, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8f);
        paint.setStrokeCap(Paint.Cap.ROUND);

        canvas.drawLine(x - size/3, y, x + size/3, y, paint);
    }

    private void drawPlus(Canvas canvas, float x, float y, float size, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8f);
        paint.setStrokeCap(Paint.Cap.ROUND);

        canvas.drawLine(x - size/3, y, x + size/3, y, paint);
        canvas.drawLine(x, y - size/3, x, y + size/3, paint);
    }

    private void drawRotationArrow(Canvas canvas, float x, float y, float size, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f);
        paint.setStrokeCap(Paint.Cap.ROUND);

        RectF ovalRect = new RectF(x - size/3, y - size/3, x + size/3, y + size/3);
        canvas.drawArc(ovalRect, -90, 270, false, paint);

        // Arrow head
        Path arrowPath = new Path();
        arrowPath.moveTo(x + size/3, y);
        arrowPath.lineTo(x + size/4, y - size/6);
        arrowPath.lineTo(x + size/4, y + size/6);
        arrowPath.close();

        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(arrowPath, paint);
    }

    private void drawWarning(Canvas canvas, float x, float y, float size, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f);
        paint.setStrokeJoin(Paint.Join.ROUND);

        Path trianglePath = new Path();
        trianglePath.moveTo(x, y - size/3);
        trianglePath.lineTo(x - size/3, y + size/3);
        trianglePath.lineTo(x + size/3, y + size/3);
        trianglePath.close();

        canvas.drawPath(trianglePath, paint);

        // Exclamation mark
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x, y + size/6, 4f, paint);
        canvas.drawRect(x - 3f, y - size/6, x + 3f, y, paint);
    }

    private void drawFaceOutline(Canvas canvas, float x, float y, float size, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f);

        // Draw simple face outline
        canvas.drawCircle(x, y, size/3, paint);

        // Eyes
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x - size/6, y - size/8, 4f, paint);
        canvas.drawCircle(x + size/6, y - size/8, 4f, paint);

        // Mouth
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        RectF mouthRect = new RectF(x - size/8, y, x + size/8, y + size/6);
        canvas.drawArc(mouthRect, 0, 180, false, paint);
    }

    // Method to get the guide oval bounds (useful for positioning other UI elements)
    public RectF getGuideOvalBounds() {
        return new RectF(guideOval);
    }

    // Method to check if face is properly positioned within the guide
    // Method to check if face is properly positioned within the guide
    // Inside FaceOverlayView.java
    public boolean isFaceInGuide(RectF faceRect) {
        // Add extensive logging to see the actual rectangles being compared
        Log.d("FaceOverlayView_DEBUG", "isFaceInGuide called");
        Log.d("FaceOverlayView_DEBUG", "  Input faceRect: " + (faceRect != null ? faceRect.toString() : "null"));
        Log.d("FaceOverlayView_DEBUG", "  Guide Oval (guideOval): " + guideOval.toString());
        Log.d("FaceOverlayView_DEBUG", "  View Dimensions - Width: " + getWidth() + ", Height: " + getHeight());
// Add check for view size and guideOval initialization
        if (getWidth() <= 0 || getHeight() <= 0 || guideOval.isEmpty()) {
            Log.w("FaceOverlayView_DEBUG", "View not laid out or guideOval not initialized yet.");
            return false; // Cannot determine if face is in guide
        }
        if (faceRect == null) {
            Log.d("FaceOverlayView_DEBUG", "  Result: false (faceRect is null)");
            return false;
        }

        // Check if rectangles overlap at all (before calculating area)
        boolean simpleOverlap = RectF.intersects(faceRect, guideOval);
        Log.d("FaceOverlayView_DEBUG", "  RectF.intersects(faceRect, guideOval): " + simpleOverlap);

        if (!simpleOverlap) {
            // Log individual coordinates for detailed analysis
            Log.d("FaceOverlayView_DEBUG", "  Detailed Coordinates:");
            Log.d("FaceOverlayView_DEBUG", "    faceRect: L=" + faceRect.left + ", T=" + faceRect.top + ", R=" + faceRect.right + ", B=" + faceRect.bottom);
            Log.d("FaceOverlayView_DEBUG", "    guideOval: L=" + guideOval.left + ", T=" + guideOval.top + ", R=" + guideOval.right + ", B=" + guideOval.bottom);
            Log.d("FaceOverlayView_DEBUG", "  Result: false (No simple intersection)");
            return false; // No intersection at all
        }

        // Calculate overlap percentage only if they intersect
        RectF intersection = new RectF();
        boolean intersects = intersection.setIntersect(faceRect, guideOval); // This should now be true
        Log.d("FaceOverlayView_DEBUG", "  intersection.setIntersect result: " + intersects);
        Log.d("FaceOverlayView_DEBUG", "  Calculated intersection: " + (intersects ? intersection.toString() : "N/A"));

        if (!intersects) {
            // This branch should ideally not be reached if simpleOverlap was true, but let's be safe.
            Log.d("FaceOverlayView_DEBUG", "  Result: false (setIntersect failed despite RectF.intersects=true)");
            return false;
        }

        float intersectionArea = intersection.width() * intersection.height();
        float faceArea = faceRect.width() * faceRect.height();
        // Avoid division by zero
        if (faceArea <= 0) {
            Log.d("FaceOverlayView_DEBUG", "  Result: false (faceArea is zero or negative)");
            return false;
        }
        float overlapPercentage = intersectionArea / faceArea;

        Log.d("FaceOverlayView_DEBUG", "  intersectionArea: " + intersectionArea);
        Log.d("FaceOverlayView_DEBUG", "  faceArea: " + faceArea);
        Log.d("FaceOverlayView_DEBUG", "  overlapPercentage: " + overlapPercentage);

        boolean result = overlapPercentage > 0.7f;
        Log.d("FaceOverlayView_DEBUG", "  Result (overlap > 70%): " + result);
        return result; // Face should be at least 70% within the guide
    }
}