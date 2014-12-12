package uk.co.darkerwaters.heartrateanalyser;

import uk.co.darkerwaters.heartrateanalyser.ble.BleConnectionService.ConnectionState;
import uk.co.darkerwaters.heartrateanalyser.ble.HeartRateConnection.HeartRateListener;
import uk.co.darkerwaters.heartrateanalyser.ble.HeartRateDataStore;
import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.BluetoothDevice;

public abstract class FragmentBase extends Fragment implements HeartRateListener {

	protected MainActivity mainActivity;
	protected String deviceName = null;
	protected BluetoothDevice device = null;
	protected ConnectionState connectionState = ConnectionState.disconnected;
	protected HeartRateDataStore heartData = null;
	
	@Override
	public void onAttach(Activity activity) {
		this.mainActivity = (MainActivity) activity;
		this.mainActivity.setupFragment(this);
		super.onAttach(activity);
	}
	
    @Override
    public void onResume() {
        super.onResume();
        
        this.mainActivity.invalidateOptionsMenu();
    }

    @Override
    public void onPause() {
        super.onPause();
        
    }

	@Override
	public void displayData(final String deviceName, final BluetoothDevice device, final ConnectionState connectionState, final HeartRateDataStore data) {
		// this probably makes our menu invalid - showing connectivity in the menu
		if (null != this.mainActivity) {
			// have the main activity, update the menu and our display data
			this.mainActivity.invalidateOptionsMenu();
			this.mainActivity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					updateDisplayData(deviceName, device, connectionState, data);
				}
			});
		}
	}

	protected void updateDisplayData(String deviceName, BluetoothDevice device, ConnectionState connectionState, HeartRateDataStore data) {
		// store the data for later
		setData(deviceName, device, connectionState, data);
	}

	public void setData(String deviceName, BluetoothDevice device, ConnectionState connectionState, HeartRateDataStore data) {
		this.deviceName = deviceName;
		this.device = device;
		if (null != this.device && null != this.device.getName()) {
			// the device has a name, ensure we use this
			this.deviceName = this.device.getName();
		}
		this.connectionState = connectionState;
		this.heartData = data;
	}
	
	public String getDeviceName() {
		return this.deviceName;
	}
	
	public BluetoothDevice getDevice() {
		return this.device;
	}
	
	public ConnectionState getConnectionState() {
		return this.connectionState;
	}
	
	public HeartRateDataStore getData() {
		return this.heartData;
	}
	
	public int getHeartRate() {
		int heartRate = -1;
		if (null != this.heartData) {
			Integer lastData = this.heartData.getLastData();
			if (null != lastData) {
				heartRate = lastData.intValue();
			}
		}
		return heartRate;
	}
}
