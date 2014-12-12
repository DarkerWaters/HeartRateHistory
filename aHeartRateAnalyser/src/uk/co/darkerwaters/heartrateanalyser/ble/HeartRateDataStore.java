package uk.co.darkerwaters.heartrateanalyser.ble;

import uk.co.darkerwaters.heartrateanalyser.MainActivity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;

public class HeartRateDataStore extends BleConnectionHistoryStore<Integer> {
	public HeartRateDataStore(Context context) {
		super(context, new HeartRateDataStorePackager());
		Log.i(MainActivity.TAG, "Creating new heart rate store");
		// also we want to ensure we have something for now, we can do this by storing a nothing
		// value, which will ensure there is a non-null history for the current store
		storeData (0, 0);
	}
	public static class HeartRateStoreProvider implements BleConnectionHistoryStore.Provider<Integer> {
		@Override
		public BleConnectionHistoryStore<Integer> createNewStore(Context context) {
			return new HeartRateDataStore(context);
		}
	}
	
	@Override
	public void handleGattData(BluetoothDevice device, BluetoothGattCharacteristic characteristic) {
		if (HeartRateConnection.UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
            }
            int heartRate = characteristic.getIntValue(format, 1);
            storeData(heartRate, 1);
        }
	}
	
	public static class HeartRateDataStorePackager implements StorePackager<Integer> {

		private static final int K_ALPHA = 255;
		@Override
		public Integer dataFromString(String data) {
			return Integer.parseInt(data);
		}
		@Override
		public int getNoBins() {
			return 7;
		}
		@Override
		public int getBinIndex(Integer data) {
			if (data < 60) {
				return 0;
			}
			else if (data < 91) {
				return 1;
			}
			else if (data < 110) {
				return 2;
			}
			else if (data < 128) {
				return 3;
			}
			else if (data < 147) {
				return 4;
			}
			else if (data < 165) {
				return 5;
			}
			else {
				return 6;
			}
		}
		@Override
		public String getBinName(int binIndex) {
			switch (binIndex) {
			case 0:
				return "Still";
			case 1:
				return "Resting";
			case 2:
				return "Recovery";
			case 3:
				return "Endurance";
			case 4:
				return "Aerobic";
			case 5:
				return "Anaerobic";
			case 6:
				return "Peak";
			default:
				return "unknown";
			}
		}
		@Override
		public int getBinColour(int binIndex) {
			switch (binIndex) {
			case 0:
				return Color.argb(K_ALPHA, 65,211,201);
			case 1:
				return Color.argb(K_ALPHA, 153,204,153);
			case 2:
				return Color.argb(K_ALPHA, 249,199,78);
			case 3:
				return Color.argb(K_ALPHA, 251,101,77);
			case 4:
				return Color.argb(K_ALPHA, 73,150,42);
			case 5:
				return Color.argb(K_ALPHA, 54,111,175);
			case 6:
				return Color.argb(K_ALPHA, 134,69,77);
			default:
				return Color.TRANSPARENT;
			}
		}
		@Override
		public String getFilePrefix() {
			return "hraf";
		}
	}
}
