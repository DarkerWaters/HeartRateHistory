package uk.co.darkerwaters.heartrateanalyser;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;

import uk.co.darkerwaters.heartrateanalyser.ble.BleConnectionHistory;
import uk.co.darkerwaters.heartrateanalyser.ble.BleConnectionHistoryStore;
import uk.co.darkerwaters.heartrateanalyser.ble.HeartRateDataStore;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/**
 * Custom view that shows the Stats data view
 */
public class StatsView extends ViewGroup {
    
	private PieChartView[] pieViews;
	
	private BleConnectionHistory<Integer> latestData = null;
	
	private HorizontalScrollView scrollView;
	
	private LinearLayout chartsLayout;
	
	private final LinkedList<Pair<String, Integer>> legend = new LinkedList<Pair<String, Integer>>();

	private final HashMap<PieChartView, BleConnectionHistory<Integer>> pieViewData = new HashMap<PieChartView, BleConnectionHistory<Integer>>();

	/**
     * Class constructor taking only a context. Use this constructor to create
     * {@link StatsView} objects from your own code.
     *
     * @param context
     */
    public StatsView(Context context) {
        super(context);
        // just initialise this view
        init(context);
    }

    /**
     * Class constructor taking a context and an attribute set. This constructor
     * is used by the layout engine to construct a {@link StatsView} from a set of
     * XML attributes.
     *
     * @param context
     * @param attrs   An attribute set which can contain attributes from
     *                {@link com.StatsView.android.customviews.R.styleable.PieChart} as well as attributes inherited
     *                from {@link android.view.View}.
     */
    public StatsView(Context context, AttributeSet attrs) {
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
                R.styleable.StatsView,
                0, 0
        );

        try {
            // Retrieve the values from the TypedArray and store into
            // fields of this class.
            //
            // The R.styleable.StatsView_* constants represent the index for
            // each custom attribute in the R.styleable.StatsView array.
            
        } finally {
            // release the TypedArray so that it can be reused.
            a.recycle();
        }
        // now initialise this view
        init(context);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Do nothing. Do not call the superclass method--that would start a layout pass
        // on this view's children. This view lays out its children in onSizeChanged().
    }

    /**
     * Initialize the control. This code is in a separate method so that it can be
     * called from both constructors.
     */
    private void init(Context context) {
    	// create this view, first create the scroll view to contain all the charts
    	this.scrollView = new HorizontalScrollView(context);
    	this.chartsLayout = new LinearLayout(context);
    	this.chartsLayout.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
    	this.chartsLayout.setOrientation(LinearLayout.HORIZONTAL);
    	// create the pie views
    	this.pieViews = new PieChartView[BleConnectionHistoryStore.K_MAXHISTORICFILES];
    	for (int i = 0; i < this.pieViews.length; ++i) {
        	this.pieViews[i] = new PieChartView(context);
        	this.chartsLayout.addView(this.pieViews[i]);
        	// add the click listener to show these details
        	this.pieViews[i].setOnClickListener(createPieViewClickListener(context));
    	}
    	this.scrollView.addView(this.chartsLayout);
        addView(this.scrollView);
        // set to be owner draw
        this.setWillNotDraw(false);
        
        // In edit mode it's nice to have some demo data, so add that here.
    	if (this.isInEditMode()) {
    		HeartRateDataStore store = new HeartRateDataStore(context);
    		BleConnectionHistory<Integer> data = new BleConnectionHistory<Integer>(new Date(), context, store);
    		Random random = new Random();
        	for (int i = 0; i < 100; ++i) {
        		// fill the last part of the graph with data
        		data.addData(random.nextInt(200), random.nextInt(19) + 1);
        	}
        	for (PieChartView pieView : this.pieViews) {
        		setData(pieView, data);
        	}
        }
    }
    
    private OnClickListener createPieViewClickListener(Context context) {
    	final MainActivity activity;
    	if (null != context && context instanceof MainActivity) {
    		activity = (MainActivity) context;
    	}
    	else {
    		activity = null;
    	}
		return new OnClickListener() {
			@Override
			public void onClick(View view) {
				// clicked on a pie view, show the fragment for this data
				if (null != view && view instanceof PieChartView) {
					PieChartView pieView = (PieChartView) view;
					BleConnectionHistory<Integer> history;
					synchronized (StatsView.this.pieViewData) {
			    		history = StatsView.this.pieViewData.get(pieView);
					}
					FragmentBase fragment = activity.showFragment(FragmentHistory.class);
					if (null != fragment && fragment instanceof FragmentHistory) {
						// this is the history fragment, set it's data
						((FragmentHistory)fragment).setData(history);
					}
				}
				else {
					Log.e(MainActivity.TAG, "The clicked view " + view + " is not a pie chart view");
				}
				
			}
		};
	}

	@Override
    protected void dispatchDraw(Canvas canvas) {
    	super.dispatchDraw(canvas);
    	// also we want to draw one legend for all the pie charts
    	if (null != this.pieViews && this.pieViews.length > 0) {
    		PieChartView pieView = this.pieViews[0];
    		// use this view
    		if (null != pieView) {
    			pieView.drawLegend(canvas, this.legend);
    		}
    	}
    }

	public void setData(PieChartView pieView, BleConnectionHistory<Integer> data) {
		pieView.clearData();
		if (null != data) {
			boolean isCreateLegend = this.legend.isEmpty();
	    	int[] colours = new int[data.getNoBins()];
	    	for (int i = 0; i < colours.length; ++i) {
	    		// add the data to the pie chart view
	    		String binTitle = data.getBinName(i);
	    		pieView.addData(binTitle, data.getBinFrequency(i));
	    		// also set the colour
	    		colours[i] = data.getBinColour(i);
	    		if (isCreateLegend) {
		    		// create our legend while we are in here
		    		this.legend.add(new Pair<String, Integer>(binTitle, colours[i]));
	    		}
	    	}
	    	// remember the original data set for each pie view
	    	synchronized (this.pieViewData) {
	    		this.pieViewData.put(pieView, data);
			}
	    	pieView.setTitle(data.getFileDateKey());
	    	pieView.setColours(colours);
	    	pieView.invalidate();
		}
	}

	public void historyChanged(BleConnectionHistory<Integer> history) {
		if (null == history) {
			// no good
			return;
		}
		// the data has changed, update all our pie views to this new data
		if (history != this.latestData) {
			// this is a change, perform a complete refresh of data
			BleConnectionHistoryStore<Integer> store = history.getStore();
			String[] fileDates = store.getHistoricFileDates();
			int dataIndex = fileDates.length - 1;
			int pieIndex = this.pieViews.length - 1;
			while (dataIndex >= 0 && pieIndex >= 0) {
				// wind back from the last data file, and pie chart, to show the newest data sets
				BleConnectionHistory<Integer> data = store.getHistoryData(fileDates[dataIndex--]);
				setData(this.pieViews[pieIndex--], data);
			}
			// set the latest data to be the last data
			this.latestData = store.getHistoryData(fileDates[fileDates.length - 1]);
			Log.i(MainActivity.TAG, "Stats looking at " + this.latestData.getFileDateKey());
		}
		else {
			// this is just the latest data, update the latest pie view
			setData(this.pieViews[this.pieViews.length - 1], this.latestData);
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
    	int width, height;
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
        setMeasuredDimension(width, height);
    }
    
    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        super.onSizeChanged(width, height, oldw, oldh);
        // layout our child views to the correct width and height
        this.scrollView.layout(0, 0, width, height);
        this.chartsLayout.layout(0, 0, width * this.pieViews.length, height);
        for (int i = 0; i < this.pieViews.length; ++i) {
        	PieChartView pieView = this.pieViews[i];
	        // layout our child views to the correct width and height
	        pieView.layout(width * i, 0, width * i + width, height);
        }
        this.scrollView.scrollBy(width * this.pieViews.length, 0);
    }

	public PieChartView getPieChart() {
		return this.pieViews[0];
	}
}
