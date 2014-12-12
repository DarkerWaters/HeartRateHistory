package uk.co.darkerwaters.heartrateanalyser;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.Random;

import uk.co.darkerwaters.heartrateanalyser.ble.BleConnectionHistory;
import uk.co.darkerwaters.heartrateanalyser.ble.HeartRateDataStore;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;
import android.widget.LinearLayout;

public class PieChartView extends View {

	private Point position = new Point(0, 0);
	private int radiusLength = 80;
	
    private Paint linePaint = new Paint();
    
    private ArrayList<Pair<String, Float>> data;
	private Paint textPaint;
	private Paint paintFill;
	
	private float sumOfData = 0;
	private int[] colours;
	private String title = "";
	
	private boolean isDrawLegend = false;
	
	private final LinkedList<Pair<String, Integer>> legend = new LinkedList<Pair<String, Integer>>();

    public PieChartView(Context context) {
        super(context);
        
        init(context);
    }

    public PieChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        init(context);
    }

    private void init(Context context) {
    	// create a random set of colours if none are sent us...
    	this.colours = new int[10];
    	for (int i = 0; i < this.colours.length; ++i) {
    		this.colours[i] = Color.rgb(new Random().nextInt(255), new Random().nextInt(255), new Random().nextInt(255));
    	}
    	textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setAntiAlias(true);
        textPaint.setStrokeWidth(5);
        textPaint.setTextSize(22);
        
        linePaint = new Paint();
        linePaint.setStyle(Style.STROKE);
        linePaint.setStrokeWidth(4);
        linePaint.setColor(0xFF33B5E5);
        linePaint.setAntiAlias(true);
        linePaint.setShadowLayer(4, 2, 2, 0x81000000);
        
		paintFill = new Paint(linePaint);
		paintFill.setStyle(Style.FILL);
		paintFill.setAntiAlias(true);
		
		// create the data store
		this.data = new ArrayList<Pair<String,Float>>();
		
		// In edit mode it's nice to have some demo data, so add that here.
    	if (isInEditMode()) {
    		HeartRateDataStore store = new HeartRateDataStore(context);
    		BleConnectionHistory<Integer> randomData = new BleConnectionHistory<Integer>(new Date(), context, store);
    		Random random = new Random();
        	for (int i = 0; i < 100; ++i) {
        		// fill the last part of the graph with data
        		randomData.addData(random.nextInt(200), random.nextInt(19) + 1);
        	}
        	// get the colours from the data to show them
    		int[] colours = new int[randomData.getNoBins()];
        	for (int i = 0; i < colours.length; ++i) {
        		// add the data to the pie chart view
        		String binTitle = randomData.getBinName(i);
        		addData(binTitle, randomData.getBinFrequency(i));
        		// also set the colour
        		colours[i] = randomData.getBinColour(i);
        	}
    		// set the data on the chart view
        	setTitle(randomData.getFileDateKey());
        	setColours(colours);
        }
    }

	@Override
    protected void onDraw(Canvas canvas) {
 		// draw a circle
 		canvas.drawCircle(position.x, position.y, radiusLength, linePaint);
 		// draw data on chart
 		drawData(canvas);
    }
	
	public void setIsDrawLegend(boolean isDrawLegend) {
		this.isDrawLegend = isDrawLegend;
	}
	
	public void setTitle(String title) {
		this.title = title;
	}
	
	public void setColours(int[] colours) {
		this.colours  = colours;
	}
	
	public void addData(String title, float value) {
		synchronized (this.data) {
			this.data.add(new Pair<String, Float>(title, value));
			this.sumOfData += value;
		}
	}
	
	@SuppressWarnings("unchecked")
	public LinkedList<Pair<String, Integer>> getLegend() {
		return (LinkedList<Pair<String, Integer>>) this.legend.clone();
	}
	
	public void clearData() {
		synchronized (this.data) {
			this.data.clear();
			this.sumOfData = 0f;
		}
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// first we need to determine the height of the action bar
    	int actionBarHeight = 0;
        int[] abSzAttr = new int[] { android.R.attr.actionBarSize };
        TypedArray a = getContext().obtainStyledAttributes(abSzAttr);
        try {
        	actionBarHeight = a.getDimensionPixelSize(0, -1);
        } finally {
            // release the TypedArray so that it can be reused.
            a.recycle();
        }        
    	// set our size nicely
    	int height = getMeasuredHeight();
    	int width = getMeasuredWidth();
    	if (getParent() instanceof LinearLayout) {
	    	LinearLayout parentLayout = (LinearLayout) getParent();
	    	if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
	    		// height is all of container, width is half
	    		height = parentLayout.getMeasuredHeight() - actionBarHeight;
	    		width = (int)(parentLayout.getMeasuredWidth() * 0.5f);
	    		parentLayout.setOrientation(LinearLayout.HORIZONTAL);
	    	}
	    	else {
	    		// height is 2/3 of the container, width is all of it
	    		height = (int)((parentLayout.getMeasuredHeight() - actionBarHeight) * 0.7);
	    		width = parentLayout.getMeasuredWidth();
	    		parentLayout.setOrientation(LinearLayout.VERTICAL);
	    	}
    	}
        setMeasuredDimension(width, height);
	}
	
	@Override
	protected void onSizeChanged(int width, int height, int oldw, int oldh) {
		super.onSizeChanged(width, height, oldw, oldh);
		// get safe rect
 		int rect = width > height ? height : width;
 		// calculate radius length
 		radiusLength = (int) ((rect / 2f) * 0.80);
 		// calculate position
 		int border =  (int)(radiusLength * 0.1f);
 		position = new Point((int) (width / 2f) - border, (int)(height / 2f) - border);
	}

	@SuppressWarnings("unchecked")
	protected void drawData(Canvas canvas) {
		ArrayList<Pair<String, Float>> toDraw;
		float sum;
		synchronized (this.data) {
			toDraw = (ArrayList<Pair<String, Float>>) this.data.clone();
			sum = this.sumOfData;
		}
		
		if (toDraw.size() > 0) {
			// if there is data, draw the pie chart now
			int offset = -90;
			int color = 0;
			// draw arcs of every piece
			for (int i = 0; i < toDraw.size(); i++) {
				// get the data item
				Pair<String, Float> data = toDraw.get(i);
				// get color
				if (i < colours.length) {
					color = colours[i];
				}
				paintFill.setColor(color);
				// draw the segment
				RectF oval = new RectF(position.x - radiusLength, position.y
						- radiusLength, position.x + radiusLength, position.y
						+ radiusLength);
				int sweep = Math.round(data.second / sum * 360f);
				if (sweep > 0) {
					// there is data, draw this data
					canvas.drawArc(oval, offset, sweep, true, paintFill);
					canvas.drawArc(oval, offset, sweep, true, linePaint);
					offset = offset + sweep;
					// add this to the legend to draw
					this.legend.addLast(new Pair<String, Integer>(data.first, color));
				}
			}
		}
		// draw the bottom title
		String informationText = this.title  + " " + Integer.toString((int)sum) + " items of data";
		textPaint.setTextAlign(Align.LEFT);
		float textY = getHeight() - this.radiusLength * 0.1f;
		Rect bounds = new Rect();
		textPaint.getTextBounds(informationText, 0, informationText.length(), bounds);
		canvas.drawText(informationText, 
						(getWidth() - bounds.width()) * 0.5f,
						textY, textPaint);
		if (isDrawLegend) {
			drawLegend(canvas, legend);
		}
	}
	
	public void drawLegend(Canvas canvas, LinkedList<Pair<String, Integer>> legend) {
		// draw the legend on this view
		drawLegend(canvas, legend, getWidth(), getHeight(), null);
	}

	public void drawLegend(Canvas canvas, LinkedList<Pair<String, Integer>> legend, int width, int height, int[] percentages) {
		// draw the legend on the specified view window
		// get the height of some text so we can draw nice color boxes
		Rect bounds = new Rect();
		textPaint.getTextBounds("TESting", 0, 7, bounds);
		int spacing = (int)(bounds.height() * 1.0f);
		int percentageWidth = 0;
		// also if we have percentages we will need to leave room for them too
		if (null != percentages && percentages.length == legend.size()) {
			// we have percentages and enough for each item in the legend
			textPaint.getTextBounds("100%", 0, 4, bounds);
			percentageWidth = bounds.width();
		}
		int textY = 0;
		float textBorder = textPaint.descent();
		int percentIndex = 0;
		for (Pair<String, Integer> legendItem : legend) {
			// move the Y position down
			textY += spacing;
			textPaint.setTextAlign(Align.RIGHT);
			canvas.drawText(legendItem.first, width - percentageWidth - spacing * 1.1f, textY, textPaint);
			// draw the colour box
			paintFill.setColor(legendItem.second);
			canvas.drawRect(width - percentageWidth - spacing, 
					textY - (spacing - textBorder), 
					width - percentageWidth - textBorder, 
					textY, 
					paintFill);
			if (percentageWidth > 0) {
				// draw the percentage for this
				textPaint.setTextAlign(Align.LEFT);
				canvas.drawText(Integer.toString(percentages[percentIndex++]) + "%", 
						width - percentageWidth - spacing * 0.1f, textY, textPaint);
			}
		}
		
	}

}
