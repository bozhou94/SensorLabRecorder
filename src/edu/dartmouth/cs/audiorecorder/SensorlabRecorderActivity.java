package edu.dartmouth.cs.audiorecorder;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

public class SensorlabRecorderActivity extends Activity {
	
	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			String text = msg.getData().getString(AudioRecorderService.AUDIORECORDER_NEWTEXT_CONTENT);
			mTvGenericText.setText(text);
		}
	};

	private static Handler sMessageHandler;
	
	private AudioRecorderStatusRecevier mAudioRecorderStatusRecevier;
	private ToggleButton mTbManageRecorder;
	private TextView mTvAudioRecorderStatus;
	private TextView mTvGenericText;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mAudioRecorderStatusRecevier = new AudioRecorderStatusRecevier();

		mTbManageRecorder = (ToggleButton) findViewById(R.id.tbManageRecorder);
		//mTvAudioRecorderStatus = (TextView) findViewById(R.id.tvAudioRecorderStatus);
		//mTvGenericText = (TextView) findViewById(R.id.tvGenericText);

		mTbManageRecorder.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked == AudioRecorderService.isServiceRunning.get()) {
					// the button is already coherent, do nothing
					return;
				}
				Intent intent = new Intent(SensorlabRecorderActivity.this, AudioRecorderService.class);
				if (isChecked) {
					startService(intent);
				} else {
					stopService(intent);
				}
			}
		});
	}

	@Override
	public void onStart() {
		super.onStart();
		registerReceiver(mAudioRecorderStatusRecevier, new IntentFilter(AudioRecorderService.AUDIORECORDER_ON));
		registerReceiver(mAudioRecorderStatusRecevier, new IntentFilter(AudioRecorderService.AUDIORECORDER_OFF));
	}

	@Override
	public void onStop() {
		super.onStop();
		unregisterReceiver(mAudioRecorderStatusRecevier);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}
	
	@Override
	public void onPause() {
		sMessageHandler = null;
		super.onPause();
	}

	private void updateStatus() {
		if (AudioRecorderService.isServiceRunning.get()) {
			mTbManageRecorder.setChecked(true);
			mTvAudioRecorderStatus.setText(String.format(getString(R.string.service_status), "on"));
		} else {
			mTbManageRecorder.setChecked(false);
			mTvAudioRecorderStatus.setText(String.format(getString(R.string.service_status), "off"));
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		updateStatus();
		sMessageHandler = mHandler;
	}
	
	public static Handler getHandler() {
		return sMessageHandler;
	}

	class AudioRecorderStatusRecevier extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(AudioRecorderService.AUDIORECORDER_ON)
					|| intent.getAction().equals(AudioRecorderService.AUDIORECORDER_OFF)) {
				updateStatus();
			}
		}
	}
	
}