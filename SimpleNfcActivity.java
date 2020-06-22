package com.startappz.stc.training.nfc;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SimpleNfcActivity extends Activity implements OnClickListener{
	public static final String TAG = "NFC Tag";
	
	Button mWriteButton;
	AlertDialog mWriteDialog;
	boolean mTagWriteState = false;
	
	NfcAdapter mNfcAdapter;
	PendingIntent mNfcPendingIntent;
	
	EditText mText;
	
	boolean mResumed = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "on create...");
		setContentView(R.layout.activity_nfc);
		
		mText = (EditText)findViewById(R.id.tagdata_view);
		mText.addTextChangedListener(mTextWatcher);
		
		mWriteButton = (Button)findViewById(R.id.write_button);
		mWriteButton.setOnClickListener(this);
		
		mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
		mNfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		
		Intent i = getIntent();
		processNfcIntent(i);
	}
	
	boolean writeNdefMessageToTag(NdefMessage msg, Tag tag) {
		int size = msg.toByteArray().length;
		try {
			Ndef ndef = Ndef.get(tag);
			if (ndef != null) {
				ndef.connect();
				if (!ndef.isWritable()) {
					Toast.makeText(this, "Tag is read-only.", Toast.LENGTH_SHORT).show();
					return false;
				}
				if (ndef.getMaxSize() < size) {
					Toast.makeText(this, 
							"Tag capacity is " + ndef.getMaxSize() + "bytes, message is " + size + " bytes.", Toast.LENGTH_SHORT).show();
					return false;
				}
				ndef.writeNdefMessage(msg);
				Toast.makeText(this, "Wrote message to tag.", Toast.LENGTH_SHORT).show();
				return true;
			} else {
				NdefFormatable format = NdefFormatable.get(tag);
				if (format != null) {
					try {
						format.connect();
						format.format(msg);
						Toast.makeText(this, "Formatted and wrote message to tag", Toast.LENGTH_SHORT).show();
						return true;
					} catch (IOException e) {
						Toast.makeText(this, "Failed to format tag.", Toast.LENGTH_SHORT).show();
						return false;
					}
				} else {
					Toast.makeText(this, "This doesnt support NDEF", Toast.LENGTH_SHORT).show();
					return false;
				}
			}
		} catch(Exception e) {
			Toast.makeText(this, "Failed to write tag.", Toast.LENGTH_SHORT).show();
		}
		return false;
	}
	
	private NdefMessage getTextAsNdefMessage() {
		byte[] langBytes = Locale.getDefault().getLanguage().getBytes(Charset.forName("US-ASCII"));
		Charset utfEncoding = Charset.forName("UTF-8");
		byte[] textBytes = mText.getText().toString().getBytes(utfEncoding);
		char status = (char) (langBytes.length);
		byte[] data = new byte[1 + langBytes.length + textBytes.length];
		data[0] = (byte) status;
		
		System.arraycopy(langBytes, 0, data, 1, langBytes.length);
		System.arraycopy(textBytes, 0, data, 1 + langBytes.length, textBytes.length);
		
		NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
		NdefRecord.RTD_TEXT, new byte[0], data);
		
		return new NdefMessage(new NdefRecord[] {record,
				NdefRecord.createApplicationRecord("com.")});
	}
	
	private void setupTagWriting(boolean enable) {
		if (enable) {
			IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
			mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, new IntentFilter[]{filter}, null);
		} else {
			mNfcAdapter.disableForegroundDispatch(this);
		}
	}
	
	private void setupReadDispatching(boolean enable) {
		if (enable) {
			IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
			
			try {
				filter.addDataType("text/plain");
			} catch(MalformedMimeTypeException e) {}	
			
			mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, new IntentFilter[]{filter}, null);
		} else {
			mNfcAdapter.disableForegroundDispatch(this);
		}
	}
	private void setupDeviceTransfor(boolean enable) {
		if (enable) {
			mNfcAdapter.enableForegroundNdefPush(this, getTextAsNdefMessage());
		} else{
			mNfcAdapter.disableForegroundNdefPush(this);
		}
	}
	private void writeTextToTag(Intent i) {
		Tag tag = i.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		NdefMessage msg = getTextAsNdefMessage();
		writeNdefMessageToTag(msg, tag);
		
		if (mWriteDialog != null) {
			mWriteDialog.cancel();
		}
	}
	
	private void processNfcIntent(Intent i) {
		if (i != null) {
			String action = i.getAction();
			if (action.equals(NfcAdapter.ACTION_NDEF_DISCOVERED) || action.equals(NfcAdapter.ACTION_TECH_DISCOVERED)) {
				Log.d(TAG, "reading...");
				readNfcTagInfo(i);
			} else if (action.equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {
				Log.d(TAG, "writing...");
				writeTextToTag(i);
			}
		} 
	}
	
	private void readNfcTagInfo(Intent i) {
		NdefMessage[] msgs = null;
		Parcelable[] rawMsgs = i.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
		if (rawMsgs != null) {
			msgs = new NdefMessage[rawMsgs.length];
			for (int x = 0; x < rawMsgs.length; x++) {
				msgs[x] = (NdefMessage) rawMsgs[x];
			}
		}
		String text = new String(msgs[0].getRecords()[0].getPayload());
		
		mText.setText(text.substring(3));
	}
	
	@Override
	protected void onNewIntent(Intent intent) {
		Log.d(TAG, "on new intent...");
		processNfcIntent(intent);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		setupReadDispatching(true);
		setupDeviceTransfor(true);
		mResumed = true;
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		setupReadDispatching(false);
		setupDeviceTransfor(false);
		mResumed = false;
	}
	
	private TextWatcher mTextWatcher = new TextWatcher() {

		@Override
		public void afterTextChanged(Editable arg0) {
			// TODO Auto-generated method stub
			if (mResumed) {
				mNfcAdapter.enableForegroundNdefPush(SimpleNfcActivity.this, getTextAsNdefMessage());
			}
			
		}

		@Override
		public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
				int arg3) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onTextChanged(CharSequence arg0, int arg1, int arg2,
				int arg3) {
			// TODO Auto-generated method stub
			
		}
		
		};
	
	
	@Override
	public void onClick(View arg0) {
		// TODO Auto-generated method stub
		setupReadDispatching(false);
		setupTagWriting(true);
		
		if (mWriteDialog == null) {
			mTagWriteState = true;
			mWriteDialog = new AlertDialog.Builder(this).setTitle(R.string.write_dialog_text).setOnCancelListener(new DialogInterface.OnCancelListener() {
				
				@Override
				public void onCancel(DialogInterface dialog) {
					// TODO Auto-generated method stub
					mTagWriteState = false;
					setupTagWriting(false);
					setupReadDispatching(true);
				}
			}).create();
		}
		mWriteDialog.show();
	}
	
	private void setAndroidBeam(boolean enable) {
		// Works with API 14+ only
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			if (enable){
				mNfcAdapter.setNdefPushMessageCallback(mNdefMessageCallback,this);
			} else {
				mNf
			}
		}
		
	}
	
}
