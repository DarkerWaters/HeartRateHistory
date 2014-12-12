package uk.co.darkerwaters.heartrateanalyser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AnimationUtils;

public class LineChartView extends View {

    private static final int K_LINECOLOR = 0xFF33B5E5;
	private static final int MIN_LINES = 3;
    private static final int MAX_LINES = 8;
    private static final int[] DISTANCES = { 1, 2, 5 };
    private static final float GRAPH_SMOOTHNES = 0.15f;
	private static final double K_SCALEROUNDVAL = 50.0;

    private Dynamics[] datapoints;
    private float maxValue;
    private Paint paint = new Paint();
    /** the space on the right and above to leave blank to draw things */
    private int edgeBorder = 0;
	private int lastPointColor = K_LINECOLOR;

    private Runnable animator = new Runnable() {
        @Override
        public void run() {
            boolean needNewFrame = false;
            long now = AnimationUtils.currentAnimationTimeMillis();
            for (Dynamics dynamics : datapoints) {
                dynamics.update(now);
                if (!dynamics.isAtRest()) {
                    needNewFrame = true;
                }
            }
            if (needNewFrame) {
                postDelayed(this, 20);
            }
            invalidate();
        }
    };

    public LineChartView(Context context) {
        super(context);
    }

    public LineChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Sets the y data points of the line chart. The data points are assumed to
     * be positive and equally spaced on the x-axis. The line chart will be
     * scaled so that the entire height of the view is used.
     * 
     * @param datapoints
     *            y values of the line chart
     */
    public void setChartData(float[] newDatapoints) {
        long now = AnimationUtils.currentAnimationTimeMillis();
        this.maxValue = 0f;
        if (datapoints == null || datapoints.length != newDatapoints.length) {
            datapoints = new Dynamics[newDatapoints.length];
            float value;
            for (int i = 0; i < newDatapoints.length; i++) {
                datapoints[i] = new Dynamics(50f, 0.5f);
                value = newDatapoints[i];
                datapoints[i].setPosition(value, now);
                datapoints[i].setTargetPosition(value, now);
                if (value > this.maxValue) {
                	this.maxValue = value;
                }
            }
            invalidate();
        } else {
        	// map the new values to the new data
            float value;
            if (datapoints.length > 0) {
	            // give the latest point a little bump
	            datapoints[datapoints.length - 1].setPosition(datapoints[datapoints.length - 1].getPosition() * 1.1f, now);
            }
            for (int i = 0; i < newDatapoints.length; i++) {
                value = newDatapoints[i];
                if (value < 0) {
                	// out of range, just put in place
                	datapoints[i].setPosition(value, now);
                }
                datapoints[i].setTargetPosition(value, now);
                if (value > this.maxValue) {
                	this.maxValue = value;
                }
            }
            removeCallbacks(animator);
            post(animator);
        }
        // round it up to the nearest 50
        //this.maxValue = (float)(Math.ceil(this.maxValue / K_SCALEROUNDVAL) * K_SCALEROUNDVAL);
        this.maxValue = Math.round((this.maxValue + 5f) / 10f) * 10f;
        this.maxValue = (float)(Math.ceil(this.maxValue / K_SCALEROUNDVAL) * K_SCALEROUNDVAL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
    	if (null != datapoints && datapoints.length > 0) {
	        drawBackground(canvas, this.maxValue);
	        drawLineChart(canvas, this.maxValue);
    	}
    }

    private void drawBackground(Canvas canvas, float maxValue) {
        int range = getLineDistance(maxValue);
        paint.setStyle(Style.FILL);
        paint.setColor(Color.GRAY);
        paint.setTextSize(16);
        paint.setStrokeWidth(1);
        Rect textBounds = new Rect();
        paint.getTextBounds("00", 0, 2, textBounds);
        this.edgeBorder = textBounds.width() * 2;
        for (int y = 0; y <= maxValue; y += range) {
            final int yPos = (int) getYPos(y, maxValue);

            // turn off anti alias for lines, they get crisper then
            paint.setAntiAlias(false);
            canvas.drawLine(0, yPos, getWidth(), yPos, paint);

            // turn on anti alias again for the text
            paint.setAntiAlias(true);
            paint.setTextAlign(Align.LEFT);
            canvas.drawText(String.valueOf(y), getPaddingLeft(), yPos - 2, paint);
            paint.setTextAlign(Align.RIGHT);
            canvas.drawText(String.valueOf(y), getWidth() - getPaddingRight(), yPos - 2, paint);
        }
    }

    private int getLineDistance(float maxValue) {
        long distance;
        int distanceIndex = 0;
        long distanceMultiplier = 1;
        int numberOfLines = MIN_LINES;

        do {
            distance = DISTANCES[distanceIndex] * distanceMultiplier;
            numberOfLines = (int) Math.ceil(maxValue / distance);

            distanceIndex++;
            if (distanceIndex == DISTANCES.length) {
                distanceIndex = 0;
                distanceMultiplier *= 10;

            }
        } while (numberOfLines < MIN_LINES || numberOfLines > MAX_LINES);

        return (int) distance;
    }

    private void drawLineChart(Canvas canvas, float maxValue) {
        Path path = createSmoothPath(maxValue);

        paint.setStyle(Style.STROKE);
        paint.setStrokeWidth(4);
        paint.setColor(K_LINECOLOR);
        paint.setAntiAlias(true);
        paint.setShadowLayer(4, 2, 2, 0x81000000);
        canvas.drawPath(path, paint);
        paint.setShadowLayer(0, 0, 0, 0);
        
        float lastPositionY = getYPos(this.datapoints[this.datapoints.length - 1].getPosition(), maxValue);
        float lastPositionX = getXPos(this.datapoints.length - 1);
        
        paint.setStyle(Style.FILL_AND_STROKE);
        paint.setColor(this.lastPointColor);
        float circleSize = this.edgeBorder * 0.25f;
        canvas.drawCircle(lastPositionX, lastPositionY, circleSize, paint);
    }

    private Path createSmoothPath(float maxValue) {

        Path path = new Path();
        path.moveTo(getXPos(0), getYPos(datapoints[0].getPosition(), maxValue));
        for (int i = 0; i < datapoints.length - 1; i++) {
            float thisPointX = getXPos(i);
            float thisPointY = getYPos(datapoints[i].getPosition(), maxValue);
            float nextPointX = getXPos(i + 1);
            float nextPointY = getYPos(datapoints[si(i + 1)].getPosition(), maxValue);

            float startdiffX = (nextPointX - getXPos(si(i - 1)));
            float startdiffY = (nextPointY - getYPos(datapoints[si(i - 1)].getPosition(), maxValue));
            float endDiffX = (getXPos(si(i + 2)) - thisPointX);
            float endDiffY = (getYPos(datapoints[si(i + 2)].getPosition(), maxValue) - thisPointY);

            float firstControlX = thisPointX + (GRAPH_SMOOTHNES * startdiffX);
            float firstControlY = thisPointY + (GRAPH_SMOOTHNES * startdiffY);
            float secondControlX = nextPointX - (GRAPH_SMOOTHNES * endDiffX);
            float secondControlY = nextPointY - (GRAPH_SMOOTHNES * endDiffY);

            path.cubicTo(firstControlX, firstControlY, secondControlX, secondControlY, nextPointX, nextPointY);
        }
        return path;
    }

    /**
     * Given an index in datapoints, it will make sure the the returned index is
     * within the array
     * 
     * @param i
     * @return
     */
    private int si(int i) {
        if (i > datapoints.length - 1) {
            return datapoints.length - 1;
        } else if (i < 0) {
            return 0;
        }
        return i;
    }

    private float getYPos(float value, float maxValue) {
        float height = getHeight() - (getPaddingTop() + getPaddingBottom() + this.edgeBorder);

        // scale it to the view size
        value = (value / maxValue) * height;

        // invert it so that higher values have lower y
        value = height - value;

        // offset it to adjust for padding
        value += getPaddingTop();

        return value;
    }

    private float getXPos(float value) {
        float width = getWidth() - (getPaddingLeft() + getPaddingRight() + this.edgeBorder);
        float maxValue = datapoints.length - 1;

        // scale it to the view size
        value = (value / maxValue) * width;

        // offset it to adjust for padding
        value += getPaddingLeft();

        return value;
    }

	public void setPointColor(Integer lastDataColor) {
		if (null == lastDataColor) {
			this.lastPointColor = K_LINECOLOR;
		}
		else {
			this.lastPointColor  = lastDataColor;
		}
	}

}
