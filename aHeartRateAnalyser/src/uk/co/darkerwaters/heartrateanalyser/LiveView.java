package uk.co.darkerwaters.heartrateanalyser;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/**
 * Custom view that shows the live data view
 */
public class LiveView extends ViewGroup {
	/** the number of pixels per data point to show */
	private static final double K_PXPERDATAPOINT = 10.0;
	
	private HorizontalScrollView scrollView;
	
	private LineChartView graphView;

	private float[] chartData = new float[0];

	/**
     * Class constructor taking only a context. Use this constructor to create
     * {@link LiveView} objects from your own code.
     *
     * @param context
     */
    public LiveView(Context context) {
        super(context);
        init(context);
    }

    /**
     * Class constructor taking a context and an attribute set. This constructor
     * is used by the layout engine to construct a {@link LiveView} from a set of
     * XML attributes.
     *
     * @param context
     * @param attrs   An attribute set which can contain attributes from
     *                {@link com.LiveView.android.customviews.R.styleable.PieChart} as well as attributes inherited
     *                from {@link android.view.View}.
     */
    public LiveView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // attrs contains the raw values for the XML attributes
        // that were specified in the layout, which don't include
        // attributes set by styles or themes, and which may have
        // unresolved references. Call obtainStyledAttributes()
        // to get the final values for each attribute.
        //
        // This call uses R.styleable.PieChart, which is an array of
        // the custom attributes that were declared in attrs.xml.
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.LiveView,
                0, 0
        );

        try {
            // Retrieve the values from the TypedArray and store into
            // fields of this class.
            //
            // The R.styleable.LiveView_* constants represent the index for
            // each custom attribute in the R.styleable.LiveView array.
            
        } finally {
            // release the TypedArray so that it can be reused.
            a.recycle();
        }

        init(context);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Do nothing. Do not call the superclass method--that would start a layout pass
        // on this view's children. this view lays out its children in onSizeChanged().
    }

    /**
     * Initialize the control. This code is in a separate method so that it can be
     * called from both constructors.
     * @param context 
     */
    private void init(final Context context) {
        // setup the child views, a graph in a scrolling window        
        this.scrollView = new HorizontalScrollView(context);
        this.graphView = new LineChartView(context);
        this.scrollView.addView(this.graphView);
        addView(this.scrollView);
        fillChartData();
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
    	int width, height;
    	LinearLayout parentLayout = (LinearLayout) getParent();
    	if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
    		// height is all of container, width is half
    		height = parentLayout.getMeasuredHeight() - actionBarHeight;
    		width = (int)(parentLayout.getMeasuredWidth() * 0.5f);
    		parentLayout.setOrientation(LinearLayout.HORIZONTAL);
    	}
    	else {
    		// height is 1/3 of the container, width is all of it
    		height = (int)((parentLayout.getMeasuredHeight() - actionBarHeight) * 0.3);
    		width = parentLayout.getMeasuredWidth();
    		parentLayout.setOrientation(LinearLayout.VERTICAL);
    	}
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        super.onSizeChanged(width, height, oldw, oldh);
        // layout our child views to the correct width and height
        this.scrollView.layout(0, 0, width, height);
        // make the graph scroll some to show more than we can see all the time
        int graphWidth = 3 * width;
        this.graphView.layout(0, 0, graphWidth, height);
        this.scrollView.scrollBy(graphWidth, 0);
        
        int chartDataSize = (int)(graphWidth/ (double)K_PXPERDATAPOINT);
        if (this.chartData.length != chartDataSize) {
        	// this is a change in data, reset the data
            this.chartData = new float[chartDataSize];
        	fillChartData();
        }
    }

	private void fillChartData() {
		// initialise our data
    	Arrays.fill(this.chartData, -1f);
    	// In edit mode it's nice to have some demo data, so add that here.
        if (this.isInEditMode()) {
        	Random random = new Random();
        	for (int i = 0; i < this.chartData.length; ++i) {
        		// fill the last part of the graph with data
        		this.chartData[i] = 40f + (random.nextFloat() * 150f);
        	}
        }
        // set the initial chart data
        graphView.setChartData(this.chartData);
	}

	public void setLatestData(float data) {
		// move the data back
		for (int i = 0; i < this.chartData.length - 1; ++i) {
			this.chartData[i] = this.chartData[i+1];
		}
		if (null != this.chartData && this.chartData.length > 0) {
			// and set the latest
			this.chartData[chartData.length - 1] = data;
			graphView.setChartData(this.chartData);
		}
	}

	public void setData(LinkedList<Integer> data, Integer lastDataColor) {
		// place all the values we want into chart data
		for (int i = this.chartData.length - 1; i >= 0 && false == data.isEmpty(); --i) {
			// from the latest to the earliest, remove data from the list to put in the array
			this.chartData[i] = data.removeLast();
		}
		// and set on the view
		graphView.setChartData(this.chartData);
		graphView.setPointColor(lastDataColor);
	}
}
