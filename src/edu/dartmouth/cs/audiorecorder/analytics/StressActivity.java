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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * This is the analytic portion of StressSense.
 * 
 * Displays: 
 * 		Status of the current processed audio History of the previous
 * 		processed audio statuses
 */
public class StressActivity extends Activity {

	// Layout Components
	private TextView mTvGenericText;
	private LinkedList<String> mList;
	private ArrayAdapter<String> mAdapter;

	// Used for upload stream and status writing
	private static StressSenseProbeWriter probeWriter;
	private static Handler sMessageHandler;
	private String message;

	// BroadcastReceiver for getting On/Off signals from the service
	private AudioRecorderStatusRecevier mAudioRecorderStatusReceiver;

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

		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
				SensorPreferenceActivity.IS_ON, false))
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
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		probeWriter.close();
	}

	/*--------------------------------BROADCASTRECEIVERS--------------------------------*/

	/**
	 * Listens for the On/Off signals given by AudioRecorderService and displays
	 * accordingly
	 */
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

	/**
	 * Receives data from setActivityText() in RehearsalAudioRecorder and
	 * displays it to the user and writes it to the upload stream
	 * 
	 * If nothing was added to the list in the previous minute, then the current
	 * status is added, with the list storing a maximum of 10 statuses
	 */
	Handler mHandler = new Handler() {

		private String prevTime;
		
		@Override
		public void handleMessage(Message msg) {
			String text = msg.getData().getString(
					AudioRecorderService.AUDIORECORDER_NEWTEXT_CONTENT);
			
			if (!text.equals(message)) {
				message = text;
				mTvGenericText.setText(": " + message);
			}
			
			String curTime = new SimpleDateFormat("h:mm a").format(Calendar
					.getInstance().getTime());
			
			if (prevTime == null || !prevTime.equals(curTime)) {
				mList.addFirst(curTime + ": " + message);
				if (mList.size() > 10)
					mList.removeLast();
				prevTime = curTime;
				mAdapter.notifyDataSetChanged();
			}
			
			if (probeWriter != null) {
				ProbeBuilder probe = new ProbeBuilder();
				DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
				String nowAsString = df.format(new Date());
				probe.withTimestamp(nowAsString);
				probeWriter.write(probe, message);
			}
		}
	};

	public static Handler getHandler() {
		return sMessageHandler;
	}
}
