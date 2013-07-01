package edu.dartmouth.cs.audiorecorder.analytics;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;

import org.ohmage.probemanager.ProbeBuilder;
import org.ohmage.probemanager.StressSenseProbeWriter;

import edu.dartmouth.cs.audiorecorder.AudioRecorderService;
import edu.dartmouth.cs.audiorecorder.R;
import edu.dartmouth.cs.audiorecorder.SensorPreferenceActivity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class StressActivity extends Activity {

	private static final String TAG = "StressActivity";

	private TextView mTvGenericText;
	private AudioRecorderStatusRecevier mAudioRecorderStatusReceiver;
	private int counter = 0;
	private LinkedList<String> mList;
	private ArrayAdapter<String> mAdapter;
	private static StressSenseProbeWriter probeWriter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_analytic);
		mAudioRecorderStatusReceiver = new AudioRecorderStatusRecevier();
		mTvGenericText = (TextView) findViewById(R.id.tvStatus);
		mList = new LinkedList<String>();
		ListView myListView = (ListView) findViewById(R.id.list);
		mAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, mList);
		myListView.setAdapter(mAdapter);

		// Send signals to StressSense to see the status
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		if (prefs.getBoolean(SensorPreferenceActivity.IS_ON, false))
			mTvGenericText.setCompoundDrawablesWithIntrinsicBounds(
					R.drawable.mic_on, 0, 0, 0);
		
		probeWriter = new StressSenseProbeWriter(this);
		probeWriter.connect();
	}

	@Override
	public void onStart() {
		super.onStart();
		registerReceiver(mAudioRecorderStatusReceiver, new IntentFilter(
				AudioRecorderService.AUDIORECORDER_ON));
		registerReceiver(mAudioRecorderStatusReceiver, new IntentFilter(
				AudioRecorderService.AUDIORECORDER_OFF));
		sMessageHandler = mHandler;
	}

	@Override
	public void onStop() {
		super.onStop();
		unregisterReceiver(mAudioRecorderStatusReceiver);
		sMessageHandler = null;
		probeWriter.close();
	}

	class AudioRecorderStatusRecevier extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {

			if (intent.getAction()
					.equals(AudioRecorderService.AUDIORECORDER_ON))
				mTvGenericText.setCompoundDrawablesWithIntrinsicBounds(
						R.drawable.mic_on, 0, 0, 0);
			else if (intent.getAction().equals(
					AudioRecorderService.AUDIORECORDER_OFF))
				mTvGenericText.setCompoundDrawablesWithIntrinsicBounds(
						R.drawable.mic_off, 0, 0, 0);
		}
	}

	/*-------------------------------HANDLER FUNCTIONALITY-------------------------------*/

	private String message;

	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			message = msg.getData().getString(
					AudioRecorderService.AUDIORECORDER_NEWTEXT_CONTENT);
			mTvGenericText.setText(": " + message);
			if (counter == 0) {
				if (mList.size() > 10)
					mList.removeLast();
				mList.addFirst(new SimpleDateFormat("h:mm a").format(Calendar
						.getInstance().getTime()) + ": " + message);
				mAdapter.notifyDataSetChanged();
				counter++;
			} else if (counter < 55)
				counter++;
			else
				counter = 0;
			if (probeWriter != null) {
				ProbeBuilder probe = new ProbeBuilder();
				DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
				String nowAsString = df.format(new Date());
				probe.withTimestamp(nowAsString);
				probeWriter.write(probe, message);
			}
		}
	};

	private static Handler sMessageHandler;

	public static Handler getHandler() {
		return sMessageHandler;
	}
}
