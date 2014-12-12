package uk.co.darkerwaters.heartrateanalyser;

import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;

public class PulseIconView extends View {
	
	private static final long K_TIMER_TICK = 100l;

	private boolean isShowText;

	private Drawable heart;
	
	private Timer timer;
    
	private long lastUpdate;
	private long averageTime = 1000l;
	private int alphaStep = 0;

	private int alpha = 0;
	
	private String displayValue = "";
	
	private Paint paint = new Paint();
	
	//we are going to use a handler to be able to run in our TimerTask 
	private final Handler handler = new Handler();
	
	public PulseIconView(Context context) {
		super(context);
		initView(context);
	}

	public PulseIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // get our attributes
		TypedArray sytleAttributes = context.getTheme().obtainStyledAttributes(attrs, R.styleable.PulseIconView, 0, 0);
		try {
			this.isShowText = sytleAttributes.getBoolean(R.styleable.PulseIconView_showIconText, false);
		} finally {
			sytleAttributes.recycle();
		}
		initView(context);
	}
	
	private void initView(Context context) {
        // set to be owner draw
        this.setWillNotDraw(false);
		// setup the paint for drawing text
		this.paint.setStyle(Style.FILL);
        this.paint.setColor(Color.GRAY);
        this.paint.setTextAlign(Align.LEFT);
        this.paint.setTextSize(16);
        this.paint.setStrokeWidth(1);
        this.paint.setAntiAlias(true);
        // setup the heart icon now
		this.heart = getResources().getDrawable(R.drawable.heart_icon);
		this.lastUpdate = System.currentTimeMillis();
		if (this.isInEditMode()) {
			// just show the icon and some text
			this.displayValue = "888";
		}
		else {
			// start the timer to dim the view
			startTimer(); 
		}
	}
	
	public void pulseIcon(String displayValue, boolean isReset) {
		this.alpha  = 255;
		this.displayValue = displayValue;
		long delta = System.currentTimeMillis() - this.lastUpdate;
		if (isReset) {
			this.averageTime = 1000;
		}
		else {
			this.averageTime = (this.averageTime + delta) / 2;
		}
		this.lastUpdate = System.currentTimeMillis();
		// calculate the required step in alpha
		this.alphaStep = (int) (this.averageTime / K_TIMER_TICK); 
	}
	  
    public void startTimer() { 
        //set a new Timer 
    	this.timer = new Timer();          
        //schedule the timer, run every 100ms 
        this.timer.schedule(new TimerTask() {
            public void run() {
            	// calculate the average time
                alpha -= alphaStep;
                handler.post(new Runnable() {
                    public void run() { 
                    	invalidate();
                    } 
                }); 
            } 
        }, K_TIMER_TICK, K_TIMER_TICK);
    } 
  
    public void stoptimertask() {
        //stop the timer, if it's not already null 
        if (this.timer != null) {
        	this.timer.cancel();
        	this.timer = null;
        } 
    } 
	
	@Override
	protected void onDraw(Canvas canvas) {
		this.heart.setBounds(0, 0, getWidth(), getHeight());
		this.heart.setAlpha(Math.max(0, alpha));
		this.heart.draw(canvas);
		if (this.isShowText) {
	        canvas.drawText(this.displayValue, getWidth() * 0.5f, getHeight() * 0.5f, paint);
		}
	}
	
	public boolean isShowText() {
		return this.isShowText;
	}

	public void setShowText(boolean showText) {
		this.isShowText = showText;
		invalidate();
		requestLayout();
	}
}
