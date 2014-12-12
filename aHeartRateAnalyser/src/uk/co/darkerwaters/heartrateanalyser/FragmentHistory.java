package uk.co.darkerwaters.heartrateanalyser;


import java.util.Date;

import uk.co.darkerwaters.heartrateanalyser.ble.BleConnectionHistory;
import uk.co.darkerwaters.heartrateanalyser.ble.BleConnectionService.ConnectionState;
import uk.co.darkerwaters.heartrateanalyser.ble.HeartRateDataStore;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;

/**
 * A fragment containing a the history views.
 */
public class FragmentHistory extends FragmentBase {
	
	private PieChartLegendView legendView;
	private PieChartView pieChartView;
	private Button resetButton;
	
	private BleConnectionHistory<Integer> data = null;

	public FragmentHistory() {
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_history, container, false);
		// find the members
		this.legendView = (PieChartLegendView) rootView.findViewById(R.id.view_history_legend);
		this.pieChartView = (PieChartView) rootView.findViewById(R.id.view_history_stats);
		this.resetButton = (Button) rootView.findViewById(R.id.history_reset_button);
		setupResetButton(container.getContext());
    	// ensure we have the data showing on this view
		setData(this.data);
		// return the root view
		return rootView;
	}
	
	private void setupResetButton(final Context context) {
		this.resetButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				// check they want to reset this data before doing it
				AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
				// set title
				alertDialogBuilder.setTitle(context.getString(R.string.history_reset));
				String messageString = context.getString(R.string.history_reset_confirm);
				messageString = messageString.replace("$DATA$", data == null ? "unknown" : data.getFileDateKey());	 
				// set dialog message
				alertDialogBuilder
					.setMessage(messageString)
					.setCancelable(false)
					.setPositiveButton(R.string.dialog_yes,new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,int id) {
							// if this button is clicked, clear out the historic data
							clearHistoryData();
						}
					  })
					.setNegativeButton(R.string.dialog_no,new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,int id) {
							// if this button is clicked, just close
							// the dialog box and do nothing
							dialog.cancel();
						}
					});
				// create alert dialog and show it
				alertDialogBuilder.create().show();
			}
		});
	}

	private void clearHistoryData() {
		// clear all the data in our current store
		if (null != this.data) {
			// clear all the historic data in this store
			this.data.clearAllHistoricData();
		}
	}

	void setData(BleConnectionHistory<Integer> data) {
		// remember the data to show
		this.data = data;
		if (null != this.resetButton) {
			this.resetButton.setClickable(null != this.data);
			this.resetButton.setEnabled(null != this.data);
		}
		if (null != this.pieChartView) {
			// clear any current data
			this.pieChartView.clearData();
			if (null != this.data) {
				// get the colours from the data to show them
				int[] colours = new int[this.data.getNoBins()];
		    	for (int i = 0; i < colours.length; ++i) {
		    		// add the data to the pie chart view
		    		String binTitle = this.data.getBinName(i);
		    		this.pieChartView.addData(binTitle, this.data.getBinFrequency(i));
		    		// also set the colour
		    		colours[i] = this.data.getBinColour(i);
		    	}
				// set the data on the chart view
		    	this.pieChartView.setTitle(this.data.getFileDateKey());
		    	this.pieChartView.setColours(colours);
		    	this.pieChartView.invalidate();
			}
		}
		if (null != this.legendView) {
			// update our legend drawing
			this.legendView.setData(this.pieChartView, this.data);
	    	this.legendView.invalidate();
		}
		if (null != this.mainActivity) {
			if (null != this.data) {
		    	// show this data title on the main bar
		    	this.mainActivity.getActionBar().setTitle(this.data.getFileDateKey());
			}
			else {
				this.mainActivity.getActionBar().setTitle(R.string.history);
			}
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		// Inflate the menu; this adds items to the action bar if it is present.
		inflater.inflate(R.menu.history, menu);
		// ensure the correct data is set on this
		updateDisplayData(this.deviceName, this.device, this.connectionState, this.heartData);
	}
	
	@Override
	protected void updateDisplayData(String deviceName, BluetoothDevice device, ConnectionState connectionState, HeartRateDataStore data) {
		super.updateDisplayData(deviceName, device, connectionState, data);
		// get the history data to show
		BleConnectionHistory<Integer> history = null;
		if (null != this.heartData) {
			// have heart data from which to set the data, do we have history?
			history = this.heartData.getHistoryData(new Date());
		}
		if (history == this.data) {
			// this is an update of our data, update our views to reflect this
			setData(history);
		}
	}
}
