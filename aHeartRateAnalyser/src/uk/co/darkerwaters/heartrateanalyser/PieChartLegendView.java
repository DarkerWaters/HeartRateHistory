package uk.co.darkerwaters.heartrateanalyser;

import java.util.Date;
import java.util.LinkedList;
import java.util.Random;

import uk.co.darkerwaters.heartrateanalyser.ble.BleConnectionHistory;
import uk.co.darkerwaters.heartrateanalyser.ble.HeartRateDataStore;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.ViewGroup;

/**
 * Custom view that shows a pie chart legend with associated data
 */
public class PieChartLegendView extends ViewGroup {
    
	private PieChartView pieView = null;
	
	private final LinkedList<Pair<String, Integer>> legend = new LinkedList<Pair<String, Integer>>();
	
	private int[] percentages = null;
	
	private Paint textPaint = new Paint();
	private Paint paintFill = new Paint();
    private Paint linePaint = new Paint();

	/**
     * Class constructor taking only a context. Use this constructor to create
     * {@link PieChartLegendView} objects from your own code.
     *
     * @param context
     */
    public PieChartLegendView(Context context) {
        super(context);
        // just initialise this view
        init(context);
    }

    /**
     * Class constructor taking a context and an attribute set. This constructor
     * is used by the layout engine to construct a {@link PieChartLegendView} from a set of
     * XML attributes.
     *
     * @param context
     * @param attrs   An attribute set which can contain attributes from
     *                {@link com.StatsView.android.customviews.R.styleable.PieChart} as well as attributes inherited
     *                from {@link android.view.View}.
     */
    public PieChartLegendView(Context context, AttributeSet attrs) {
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
                R.styleable.PieChartLegendView,
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
        // set to be owner draw
        this.setWillNotDraw(false);
        
        textPaint.setColor(Color.WHITE);
        textPaint.setAntiAlias(true);
        textPaint.setStrokeWidth(5);
        textPaint.setTextSize(22);
        
        linePaint.setStyle(Style.STROKE);
        linePaint.setStrokeWidth(4);
        linePaint.setColor(0xFF33B5E5);
        linePaint.setAntiAlias(true);
        linePaint.setShadowLayer(4, 2, 2, 0x81000000);
        
		paintFill = new Paint(linePaint);
		paintFill.setStyle(Style.FILL);
		paintFill.setAntiAlias(true);
		
		// In edit mode it's nice to have some demo data, so add that here.
    	if (isInEditMode()) {
    		HeartRateDataStore store = new HeartRateDataStore(context);
    		BleConnectionHistory<Integer> randomData = new BleConnectionHistory<Integer>(new Date(), context, store);
    		Random random = new Random();
        	for (int i = 0; i < 100; ++i) {
        		// fill the last part of the graph with data
        		randomData.addData(random.nextInt(200), random.nextInt(19) + 1);
        	}
        	setData(new PieChartView(context), randomData);
        }
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
    	super.onDraw(canvas);
    	// also we want to draw the legend
    	if (null != this.pieView && this.legend.size() > 0) {
    		this.pieView.drawLegend(canvas, this.legend, getWidth(), getHeight(), percentages);
    	}
    }

	public void setData(PieChartView pieChartView, BleConnectionHistory<Integer> data) {
		this.pieView = pieChartView;
		if (null != data) {
			this.legend.clear();
			int total = 0;
	    	int[] colours = new int[data.getNoBins()];
	    	for (int i = 0; i < colours.length; ++i) {
		    	// create our legend of data from that set
		    	this.legend.add(new Pair<String, Integer>(data.getBinName(i), data.getBinColour(i)));
		    	// add up the total
		    	total += data.getBinFrequency(i);
	    	}
	    	// calculate our percentages of data
	    	this.percentages = new int[colours.length];
	    	for (int i = 0; i < this.percentages.length; ++i) {
	    		this.percentages[i] = (int)((float)data.getBinFrequency(i) / total * 100f);
	    	}
		}
	}
}
