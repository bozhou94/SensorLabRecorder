package edu.dartmouth.cs.audiorecorder.lite;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.ohmage.mobility.blackout.BlackoutDesc;
import org.ohmage.mobility.blackout.base.TriggerDB;
import org.ohmage.mobility.blackout.utils.SimpleTime;
import org.ohmage.probemanager.StressSenseProbeWriter;

import edu.dartmouth.cs.audiorecorder.AudioRecorderService;
import edu.dartmouth.cs.audiorecorder.R;
import edu.dartmouth.cs.audiorecorder.SensorPreferenceActivity;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.media.AudioFormat;
import android.media.MediaRecorder.AudioSource;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

public class AudioRecorderServiceLite extends Service {

	public static final String AUDIORECORDER_STRING_ID = "edu.dartmouth.cs.audiorecorder.AudioRecorder";

	public static final String AUDIORECORDER_ACTION = "edu.dartmouth.cs.audiorecorder.AudioRecorder.ACTION";

	public static final String AUDIORECORDER_ACTION_START = "edu.dartmouth.cs.audiorecorder.AudioRecorder.action.START";
	public static final String AUDIORECORDER_ACTION_STOP = "edu.dartmouth.cs.audiorecorder.AudioRecorder.action.STOP";

	private static final String AUDIO_RECORDING_DIR = "rawaudio";
	private static final int WAV_CHUNK_LENGTH_MS = 5 * 60 * 1000; // 5 minutes
	private static final int BLACKOUT_NOTIFICATION_ID = 201308050;

	private static final String TAG = "AudioRecorderService";
	private PowerManager.WakeLock mWl;

	private RehearsalAudioRecorderLite mWavAudioRecorder;

	private IncomingCallDetector mIncomingCallDetector;
	private OutgoingCallDetector mOutgoingCallDetector;
	private TimeChangeReceiver mTimeChangeReceiver;
	private NotificationManager mNotifManager;

	// Blackout functionality
	private Handler handler = new Handler();
	private Runnable Blackout;
	private TriggerDB db;
	private Cursor c;
	private boolean isRecording = false;
	public static StressSenseProbeWriter probeWriter;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	// //////////////////////////////////////////////////////////////////////////////////
	// All code below this line is for internal develpment only
	// //////////////////////////////////////////////////////////////////////////////////

	@Override
	public void onCreate() {
		Log.i(TAG, "onCreate()");
		try {

			mWl = ((PowerManager) getSystemService(Context.POWER_SERVICE))
					.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
							AudioRecorderServiceLite.class.getName());
			mWl.acquire();

			probeWriter = new StressSenseProbeWriter(this);

			mWavAudioRecorder = new RehearsalAudioRecorderLite(AudioSource.MIC,
					8000, AudioFormat.CHANNEL_IN_MONO,
					AudioFormat.ENCODING_PCM_16BIT);
			mIncomingCallDetector = new IncomingCallDetector();
			mOutgoingCallDetector = new OutgoingCallDetector();
			mTimeChangeReceiver = new TimeChangeReceiver();
			registerReceiver(mIncomingCallDetector, new IntentFilter(
					"android.intent.action.PHONE_STATE"));
			registerReceiver(mOutgoingCallDetector, new IntentFilter(
					Intent.ACTION_NEW_OUTGOING_CALL));
			registerReceiver(mTimeChangeReceiver, new IntentFilter(
					Intent.ACTION_TIME_TICK));

			/*
			 * The Handler calls the Blackout runnable almost every minute to
			 * see if the time coincides with a user-dictated Blackout time. It
			 * then starts/stops recording accordingly.
			 */
			db = new TriggerDB(this);
			db.open();
			c = db.getAllTriggers();

			Blackout = new Runnable() {

				@Override
				public void run() {

					boolean canRunNow = true;
					SimpleTime now = new SimpleTime();

					if (c.moveToFirst()) {
						do {
							int trigId = c.getInt(c
									.getColumnIndexOrThrow(TriggerDB.KEY_ID));

							String trigDesc = db.getTriggerDescription(trigId);
							BlackoutDesc conf = new BlackoutDesc();

							if (!conf.loadString(trigDesc)
									|| !db.getActionDescription(trigId)) {
								continue;
							}

							SimpleTime start = conf.getRangeStart();
							SimpleTime end = conf.getRangeEnd();
							if (!start.isAfter(now) && !end.isBefore(now))
								canRunNow = false;

						} while (c.moveToNext());
					}
					if (canRunNow && !isRecording) {
						startRecoding(true);
					} else if (!canRunNow && isRecording) {
						stopRecording(true);
					}
				}
			};

			handler.post(Blackout);
			mNotifManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
			e.printStackTrace();
			stopSelf();
		}

	}

	@Override
	public void onDestroy() {
		unregisterReceiver(mIncomingCallDetector);
		unregisterReceiver(mOutgoingCallDetector);
		unregisterReceiver(mTimeChangeReceiver);
		mWl.release();
		if (isRecording)
			stopRecording(true);
		mWavAudioRecorder.release();
		handler.removeCallbacks(Blackout);
		c.close();
		db.close();
		mNotifManager.cancel(BLACKOUT_NOTIFICATION_ID);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// start the service on foreground to avoid it being killed too soon.
		CharSequence text = getText(R.string.foreground_lite_service_started);
		Notification notification = new Notification(R.drawable.icon_small,
				text, System.currentTimeMillis());
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, SensorPreferenceActivity.class), 0);
		notification.setLatestEventInfo(this,
				getText(R.string.local_service_label), text, contentIntent);
		startForeground(BLACKOUT_NOTIFICATION_ID, notification);

		// If we get killed, after returning from here, restart
		return START_STICKY;

	}

	private void stopRecording(boolean cancelTimer) {
		CharSequence text = getText(R.string.audiorecording_lite_service_stopped);
		Notification notification = new Notification(R.drawable.micoff_small,
				text, System.currentTimeMillis());
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, SensorPreferenceActivity.class), 0);
		notification.setLatestEventInfo(this,
				getText(R.string.audiorecording_service_stopped), text,
				contentIntent);

		mNotifManager.notify(BLACKOUT_NOTIFICATION_ID, notification);

		if (mWavAudioRecorder.getState() == RehearsalAudioRecorderLite.State.RECORDING) {
			isRecording = false;
			if (probeWriter != null) {
				RehearsalAudioRecorderLite.setActivityText("Off");
				probeWriter.close();
			}
			mWavAudioRecorder.stop();
			Log.i(TAG, "Recording stopped");
		}
	}

	private void startRecoding(boolean startTimer) {

		CharSequence text = getText(R.string.audiorecording_lite_service_started);
		Notification notification = new Notification(R.drawable.micon_small,
				text, System.currentTimeMillis());
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, SensorPreferenceActivity.class), 0);
		notification.setLatestEventInfo(this,
				getText(R.string.audiorecording_service_started), text,
				contentIntent);

		mNotifManager.notify(BLACKOUT_NOTIFICATION_ID, notification);

		if (mWavAudioRecorder.getState() != RehearsalAudioRecorderLite.State.RECORDING) {

			probeWriter.connect();

			mWavAudioRecorder.reset();

			mWavAudioRecorder.prepare();

			mWavAudioRecorder.start();

			isRecording = true;
			Log.i(TAG, "Recording started");
		}
	}

	class IncomingCallDetector extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			String extra = intent
					.getStringExtra(android.telephony.TelephonyManager.EXTRA_STATE);
			// means call running
			if (extra
					.equals(android.telephony.TelephonyManager.EXTRA_STATE_RINGING)) {
				Log.i(TAG, "Incoming call, stop recording");
				stopRecording(true);
			}

			if (extra
					.equals(android.telephony.TelephonyManager.EXTRA_STATE_IDLE)) {
				// strategy if the phone call end then start the audio service
				Log.i(TAG, "Call ended, start recording");
				startRecoding(true);
			}
		}

	}

	class OutgoingCallDetector extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.i(TAG, "Outgoing call, stopping recording");
			stopRecording(true);
		}
	}

	class TimeChangeReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			handler.post(Blackout);
			String curTime = new SimpleDateFormat("h:mm a").format(Calendar
					.getInstance().getTime());
			if ((curTime.equals("12:00 PM") || curTime.equals("12:00 AM")) && isRecording) {
				stopRecording(true);
				mWavAudioRecorder.release();
				mWavAudioRecorder = new RehearsalAudioRecorderLite(
						AudioSource.MIC, 8000, AudioFormat.CHANNEL_IN_MONO,
						AudioFormat.ENCODING_PCM_16BIT);
				startRecoding(true);
			}
		}
	}

}