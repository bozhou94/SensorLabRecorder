package edu.dartmouth.cs.audiorecorder.analytics;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedList;

import edu.dartmouth.cs.audiorecorder.AudioRecorderService;
import edu.dartmouth.cs.audiorecorder.R;
import edu.dartmouth.cs.audiorecorder.R.drawable;
import edu.dartmouth.cs.audiorecorder.R.id;
import edu.dartmouth.cs.audiorecorder.R.layout;
import edu.dartmouth.cs.audiorecorder.SensorPreferenceActivity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

public class StressActivity extends Activity {

	private static final String TAG = "StressActivity";

	private TextView mTvGenericText;
	private AudioRecorderStatusRecevier mAudioRecorderStatusReceiver;
	private AudioRecorderDataReceiver mAudioRecorderDataReceiver;
	private Handler handler = new Handler();
	private Runnable infoRetriever;
	private int counter = 0;
	private LinkedList<String> mList;
	private ArrayAdapter<String> mAdapter;
	private boolean isServiceRunning = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_analytic);
		mAudioRecorderStatusReceiver = new AudioRecorderStatusRecevier();
		mAudioRecorderDataReceiver = new AudioRecorderDataReceiver();
		mTvGenericText = (TextView) findViewById(R.id.tvStatus);
		mList = new LinkedList<String>();
		ListView myListView = (ListView) findViewById(R.id.list);
		mAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, mList);
		myListView.setAdapter(mAdapter);

		// Send signals to StressSense to see the status
		Intent i = new Intent();
		i.setAction(SensorPreferenceActivity.ACTIVITY_LOADED);
		sendBroadcast(i);
		infoRetriever = new Runnable() {
			@Override
			public void run() {
				handler.postDelayed(infoRetriever, 1000);
				Intent i2 = new Intent();
				i2.setAction(SensorPreferenceActivity.ACTIVITY_ON);
				sendBroadcast(i2);
			}
		};
	}

	@Override
	public void onStart() {
		super.onStart();
		registerReceiver(mAudioRecorderStatusReceiver, new IntentFilter(
				AudioRecorderService.AUDIORECORDER_ON));
		registerReceiver(mAudioRecorderStatusReceiver, new IntentFilter(
				AudioRecorderService.AUDIORECORDER_OFF));
		registerReceiver(mAudioRecorderDataReceiver, new IntentFilter(
				AudioRecorderService.AUDIORECORDER_NEWTEXT_CONTENT));
		if (isServiceRunning)
			handler.post(infoRetriever);
	}

	@Override
	public void onStop() {
		super.onStop();
		unregisterReceiver(mAudioRecorderStatusReceiver);
		unregisterReceiver(mAudioRecorderDataReceiver);
		if (isServiceRunning)
			handler.removeCallbacks(infoRetriever);
	}

	class AudioRecorderStatusRecevier extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {

			if (intent.getAction().equals(AudioRecorderService.AUDIORECORDER_ON)) {
				mTvGenericText.setCompoundDrawablesWithIntrinsicBounds(
						R.drawable.mic_on, 0, 0, 0);
				isServiceRunning = true;
				handler.post(infoRetriever);
			} else if (intent.getAction().equals(AudioRecorderService.AUDIORECORDER_OFF)) {
				mTvGenericText.setCompoundDrawablesWithIntrinsicBounds(
						R.drawable.mic_off, 0, 0, 0);
				isServiceRunning = false;
				handler.removeCallbacks(infoRetriever);
			}
		}
	}

	class AudioRecorderDataReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(AudioRecorderService.AUDIORECORDER_NEWTEXT_CONTENT)) {
				String message = intent.getStringExtra("Mode");
				mTvGenericText.setText(": " + message);
				if (counter == 0) {
					if (mList.size() > 10)
						mList.removeLast();
					mList.addFirst(new SimpleDateFormat("h:mm a")
							.format(Calendar.getInstance().getTime())
							+ ": "
							+ message);
					mAdapter.notifyDataSetChanged();
					counter++;
				} else if (counter < 55)
					counter++;
				else
					counter = 0;
			}
		}
	}
}
