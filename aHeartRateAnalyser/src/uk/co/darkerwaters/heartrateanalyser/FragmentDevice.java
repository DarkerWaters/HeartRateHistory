package uk.co.darkerwaters.heartrateanalyser;

import uk.co.darkerwaters.heartrateanalyser.ble.BleConnectionService.ConnectionState;
import uk.co.darkerwaters.heartrateanalyser.ble.HeartRateDataStore;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

/**
 * A fragment containing the view of the connected device
 */
public class FragmentDevice extends FragmentBase {
    
	private TextView connectedDeviceText;
	private TextView deviceAddressText;
	private TextView deviceNameText;
    private Button deviceActionButton;
	private MainActivity mainActivity = null;
	
	public FragmentDevice() {
    }

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_device, container, false);
		
		// find the members
		this.mainActivity  = (MainActivity) container.getContext();
		this.connectedDeviceText = (TextView) rootView.findViewById(R.id.connectedDeviceText);
		this.deviceAddressText = (TextView) rootView.findViewById(R.id.device_address);
		this.deviceNameText = (TextView) rootView.findViewById(R.id.device_name);
		this.deviceActionButton = (Button) rootView.findViewById(R.id.deviceActionButton);
		this.deviceActionButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				switch(FragmentDevice.this.connectionState) {
				case connecting :
				case connected :
					FragmentDevice.this.mainActivity.disconnectDevice();
					break;
				case disconnected:
				case disconnecting :
					FragmentDevice.this.mainActivity.connectToDevice(getDevice());
					break;
		    	}
			}
		});
		// update our view now
		updateDisplayData(this.deviceName, this.device, this.connectionState, this.heartData);
		
		return rootView;
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.device, menu);
		switch(this.connectionState) {
		case connecting :
			menu.findItem(R.id.menu_connect).setVisible(false);
			menu.findItem(R.id.menu_disconnect).setVisible(true);
			menu.findItem(R.id.menu_refresh).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(R.layout.actionbar_indeterminate_progress);
            break;
		case connected :
			menu.findItem(R.id.menu_connect).setVisible(false);
			menu.findItem(R.id.menu_disconnect).setVisible(true);
			menu.findItem(R.id.menu_refresh).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(null);
            break;
		case disconnected :
		case disconnecting :
			menu.findItem(R.id.menu_connect).setVisible(true);
			menu.findItem(R.id.menu_disconnect).setVisible(false);
			menu.findItem(R.id.menu_refresh).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(null);
            break;
		}
		// update the button text etc
		updateDisplayData(this.deviceName, this.device, this.connectionState, this.heartData);
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	boolean result = false;
        switch(item.getItemId()) {
            case R.id.menu_connect:
            	this.mainActivity.connectToDevice(getDevice());
            	result = true;
            	break;
            case R.id.menu_disconnect:
            	this.mainActivity.disconnectDevice();
            	result = true;
            	break;
        }
        return result;
    }
    
	@Override
	protected void updateDisplayData(String deviceName, BluetoothDevice device, ConnectionState connectionState, HeartRateDataStore heartData) {
    	super.updateDisplayData(deviceName, device, connectionState, heartData);
    	// update our state title
    	this.connectedDeviceText.setText(this.connectionState.toString());
    	// update the button text
    	switch(this.connectionState) {
		case connecting :
		case connected :
			this.deviceActionButton.setText(R.string.action_disconnect);
			break;
		case disconnected:
		case disconnecting :
			this.deviceActionButton.setText(R.string.action_connect);
			break;
    	}
    	this.deviceAddressText.setText("unknown");
    	this.deviceNameText.setText(deviceName);
    	this.deviceAddressText.setText(device == null ? "" : device.getAddress());
    }
}
