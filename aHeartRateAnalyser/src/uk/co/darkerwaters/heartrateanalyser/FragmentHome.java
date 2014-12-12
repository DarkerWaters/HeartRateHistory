package uk.co.darkerwaters.heartrateanalyser;


import java.util.Date;
import java.util.LinkedList;

import uk.co.darkerwaters.heartrateanalyser.ble.BleConnectionHistory;
import uk.co.darkerwaters.heartrateanalyser.ble.BleConnectionService.ConnectionState;
import uk.co.darkerwaters.heartrateanalyser.ble.HeartRateDataStore;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * A fragment containing a the home views.
 */
public class FragmentHome extends FragmentBase {
	
	private LiveView liveView;
	private StatsView statsView;
	private TextView deviceTextView;

	private HeartRateView heartRateView;
	

	public FragmentHome() {
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_home, container, false);
		// find the members
		this.liveView = (LiveView) rootView.findViewById(R.id.view_live);
		this.statsView = (StatsView) rootView.findViewById(R.id.view_stats);
		this.deviceTextView = (TextView) rootView.findViewById(R.id.view_device);
		this.deviceTextView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				// show the devices
				FragmentHome.this.mainActivity.showFragment(FragmentDevices.class);
			}
		});
		// return the root view
		return rootView;
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		// set the action bar title
		this.mainActivity.getActionBar().setTitle(R.string.app_name);
		// Inflate the menu; this adds items to the action bar if it is present.
		inflater.inflate(R.menu.main, menu);
		// create the view to handle the heart rate
		this.heartRateView = HeartRateView.CreateView(this.mainActivity);
		menu.findItem(R.id.action_heart_rate).setActionView(this.heartRateView.getView());
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
		if (null != history) {
			// we can send our history to the live view
			LinkedList<Integer> recentValues = history.getRecentValues();
			Integer lastColor = null;
			if (null != recentValues && recentValues.size() > 0) {
				// get the colour of the most recent reading
				Integer lastValue = recentValues.getLast();
				if (null != lastValue) {
					lastColor = history.getBinColour(history.getBinIndex(lastValue));
				}
			}
			this.liveView.setData(history.getRecentValues(), lastColor);
			// update the stats view
			this.statsView.historyChanged(history);
		}
		// view is initialised properly
		switch (connectionState) {
		case connected:
			// update the heart rate on the live view
			if (null != this.heartData) {
				// have heart data from which to set the data, do we have history?
				Integer lastData = this.heartData.getLastData();
				if (null == history) {
					// just send the last data to the live view as we failed with the history
					this.liveView.setLatestData((float)(lastData == null ? -1 : lastData));
				}
				if (null != this.heartRateView && null != lastData) {
					this.heartRateView.updateHeartRate(Integer.toString(this.heartData.getLastData()));
				}
			}
			break;
		case disconnected:
			if (null != this.heartRateView) {
				this.heartRateView.noHeartRate();
			}
			break;
		case connecting:
			if (null != this.heartRateView) {
				this.heartRateView.waitingHeartRate();
			}
			break;
		default:
			break;
		}
		// put the device name in the view
		String deviceNameText = deviceName + " " + this.connectionState.toString();
		if (null == this.device) {
			// no device at all, show the not connected string
			deviceNameText = getString(R.string.device_not_connected);
		}
		this.deviceTextView.setText(deviceNameText);
	}
}
