package ch.uzh.csg.nfclib.transceiver;

import java.io.IOException;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;
import ch.uzh.csg.nfclib.CustomHostApduService;
import ch.uzh.csg.nfclib.NfcEvent;
import ch.uzh.csg.nfclib.NfcEventInterface;
import ch.uzh.csg.nfclib.exceptions.NfcNotEnabledException;
import ch.uzh.csg.nfclib.exceptions.NoNfcException;
import ch.uzh.csg.nfclib.exceptions.TransceiveException;

import com.acs.smartcard.Reader;
import com.acs.smartcard.Reader.OnStateChangeListener;
import com.acs.smartcard.ReaderException;

//TODO: javadoc
public class ExternalNfcTransceiver extends NfcTransceiver {
	
	private static final String TAG = "##NFC## ExternalNfcTransceiver";
	
	/*
	 * 64 is the maximum due to a sequence bug in the ACR122u
	 * http://musclecard.996296
	 * .n3.nabble.com/ACR122U-response-frames-contain-wrong
	 * -sequence-numbers-td5002.html If larger than 64, then I get a
	 * com.acs.smartcard.CommunicationErrorException: The sequence number (4) is
	 * invalid.
	 */
	protected static final int MAX_WRITE_LENGTH = 53;
	
	private static final int MAX_RAW_RETRIES = 3;
	
	private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
	
	private Reader reader;
	
	private BroadcastReceiver broadcastReceiver;

	public ExternalNfcTransceiver(NfcEventInterface eventHandler, long userId) {
		super(eventHandler, MAX_WRITE_LENGTH, userId);
	}
	
	/*
	 * This constructor is only for test purposes, in order to mock the Reader.
	 * For productive use please use the constructor above, otherwise the NFC
	 * will not work.
	 */
	protected ExternalNfcTransceiver(NfcEventInterface eventHandler, long userId, Reader reader) {
		this(eventHandler, userId);
		this.reader = reader;
	}
	
	@Override
	public void enable(Activity activity) throws NoNfcException, NfcNotEnabledException {
		Log.d(TAG, "enable NFC");
		
		UsbManager manager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
		reader = new Reader(manager);
		
		UsbDevice externalDevice = null;
		for (UsbDevice device : manager.getDeviceList().values()) {
			if (reader.isSupported(device)) {
				externalDevice = device;
				break;
			}
		}
		
		if (externalDevice == null) {
			throw new NoNfcException();
		}
		
		PendingIntent permissionIntent = PendingIntent.getBroadcast(activity, 0, new Intent(ACTION_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		broadcastReceiver = createBroadcastReceiver();
		activity.registerReceiver(broadcastReceiver, filter);
		
		manager.requestPermission(externalDevice, permissionIntent);
		
		setOnStateChangedListener();
		setEnabled(true);
	}

	@Override
	public void disable(Activity activity) {
		activity.unregisterReceiver(broadcastReceiver);
		if (reader != null && reader.isOpened()) {
			reader.close();
		}
		setEnabled(false);
	}
	
	@Override
	protected byte[] writeRaw(byte[] bytes) throws IllegalArgumentException, TransceiveException, IOException {
		if (!isEnabled()) {
			Log.d(TAG, "could not write message, NfcTransceiver is not enabled");
			throw new TransceiveException(NfcEvent.FATAL_ERROR, NFCTRANSCEIVER_NOT_CONNECTED);
		}
		
		if (reader.isOpened()) {
			if (bytes == null) {
				throw new IllegalArgumentException(NULL_ARGUMENT);
			} else if (bytes.length > MAX_WRITE_LENGTH) {
				throw new IllegalArgumentException("The message length exceeds the maximum capacity of " + MAX_WRITE_LENGTH + " bytes.");
			}
			
			byte[] result = null;
			int length = 0;
			
			for (int i=0; i<MAX_RAW_RETRIES; i++) {
				long start = System.currentTimeMillis();
				try { 
					byte[] recvBuffer = new byte[CustomHostApduService.MAX_WRITE_LENGTH];
					length = reader.transmit(0, bytes, bytes.length, recvBuffer, recvBuffer.length);
					if (length > 0) {
						result = new byte[length];
						System.arraycopy(recvBuffer, 0, result, 0, length);
						break;
					} else {
						long wait = 100 - (System.currentTimeMillis() - start);
						if (wait > 0) {
							Thread.sleep(wait);
						}
					}
				} catch (ReaderException e) {
					throw new IOException("ExternalNfcTransceiver failed to transceive raw message.");
				} catch (InterruptedException e) {
					throw new IOException("ExternalNfcTransceiver failed to transceive raw message.");
				}
			}
			
			if (length <= 0) {
				throw new IOException("ExternalNfcTransceiver failed to transceive raw message.");
			}
			
			return result;
		} else {
			Log.d(TAG, "could not write message, reader is no longer open");
			/*
			 * throw new IOException so that NfcTransceiver waits for a resume
			 */
			throw new IOException(NFCTRANSCEIVER_NOT_CONNECTED);
		}
	}
	
	private void setOnStateChangedListener() {
		reader.setOnStateChangeListener(new OnStateChangeListener() {
			
			public void onStateChange(int slotNum, int prevState, int currState) {
				Log.d(TAG, "statechange from: " + prevState + " to: " + currState);
				if (currState == Reader.CARD_PRESENT) {
					try {
						initCard();
						initNfc();
					} catch (ReaderException e) {
						Log.e(TAG, "Could not connnect reader: ", e);
						getNfcEventHandler().handleMessage(NfcEvent.INIT_FAILED, null);
					} catch (IOException e) {
						Log.e(TAG, "Could not connnect reader: ", e);
						getNfcEventHandler().handleMessage(NfcEvent.INIT_FAILED, null);
					}
            	} 
			}
		});
	}
	
	private void initCard() throws ReaderException {
		reader.power(0, Reader.CARD_WARM_RESET);
		reader.setProtocol(0, Reader.PROTOCOL_T0 | Reader.PROTOCOL_T1);
	}
	
	public static boolean isExternalReaderAttached(Activity activity) {
		UsbManager manager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
		Reader reader = new Reader(manager);
		
		for (UsbDevice device : manager.getDeviceList().values()) {
			if (reader.isSupported(device)) {
				return true;
			}
		}
		
		return false;
	}
	
	private BroadcastReceiver createBroadcastReceiver() {
		return new BroadcastReceiver() {

			@Override
            public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();

		        if (ACTION_USB_PERMISSION.equals(action)) {
		            UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
		            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
						if (device != null) {
							try {
								reader.open(device);
							} catch (Exception e) {
								getNfcEventHandler().handleMessage(NfcEvent.INIT_FAILED, null);
								setEnabled(false);
							}
						}
		            }
		        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
		            UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

		            if (device != null && device.equals(reader.getDevice())) {
		            	reader.close();
		            }
		        }
	            
            }

			
		};
	}
	

}
