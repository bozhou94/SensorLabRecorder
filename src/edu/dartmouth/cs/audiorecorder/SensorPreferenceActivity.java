package edu.dartmouth.cs.audiorecorder;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.ohmage.mobility.blackout.Blackout;
import org.ohmage.mobility.blackout.BlackoutDesc;
import org.ohmage.mobility.blackout.base.TriggerDB;
import org.ohmage.mobility.blackout.base.TriggerInit;
import org.ohmage.mobility.blackout.ui.TriggerListActivity;
import org.ohmage.mobility.blackout.utils.SimpleTime;
import org.ohmage.probemanager.ProbeBuilder;
import org.ohmage.probemanager.StressSenseProbeWriter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceClickListener;

public class SensorPreferenceActivity extends PreferenceActivity implements
		OnSharedPreferenceChangeListener {

	public static final String STRESSSENSE = "stresssense";
	public static final String ONOFF_KEY = "pref_onoff";
	public static final String BLACKOUT_KEY = "pref_key";
	//public static final String LOCATION_KEY = "pref_loc";
	public static final String IS_ON = "stresssense_on";

	private boolean running = false;
	private Preference connectionPref;
	//private CheckBoxPreference mobility_on;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		
		/*
		mobility_on = (CheckBoxPreference) findPreference(LOCATION_KEY);
		if (!MobilityHelper.isMobilityInstalled(this)) {
			removePreference(mobility_on);
		}*/
		
		
		connectionPref = findPreference(BLACKOUT_KEY);
		connectionPref.setOnPreferenceClickListener(mOnClickListener);
	}

	@Override
	protected void onResume() {
		super.onResume();
		getPreferenceScreen().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);
		running = getPreferenceScreen().getSharedPreferences().getBoolean(
				IS_ON, false);
		if (running)
			SensorPreferenceActivity.start(SensorPreferenceActivity.this
					.getApplicationContext());
	}

	@Override
	protected void onPause() {
		super.onPause();
		getPreferenceScreen().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);

	}

	/*-------------------------------PREFERENCE FUNCTIONALITY-------------------------------*/

	private final OnPreferenceClickListener mOnClickListener = new OnPreferenceClickListener() {

		@Override
		public boolean onPreferenceClick(Preference preference) {
			Intent intent = new Intent(SensorPreferenceActivity.this,
					TriggerListActivity.class);
			SensorPreferenceActivity.this.startActivity(intent);
			return false;
		}
	};

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals(ONOFF_KEY)) {
			running = !running;
			Editor editor = sharedPreferences.edit();
			editor.putBoolean(IS_ON, running);
			editor.commit();
			if (running)
				SensorPreferenceActivity.start(SensorPreferenceActivity.this
						.getApplicationContext());
			else
				SensorPreferenceActivity.stop(SensorPreferenceActivity.this
						.getApplicationContext());
		}
	}

	/*-------------------------------BLACKOUT FUNCTIONALITY-------------------------------*/

	public static void startRunning(Context context) {
		context.startService(new Intent(context, AudioRecorderService.class));
	}

	public static void stopRunning(Context context, boolean blackout) {
		context.stopService(new Intent(context, AudioRecorderService.class));
	}

	public static void start(Context context) {
		TriggerDB db = new TriggerDB(context);
		db.open();
		boolean canRunNow = true;
		Cursor c = db.getAllTriggers();
		if (c.moveToFirst()) {
			do {
				int trigId = c
						.getInt(c.getColumnIndexOrThrow(TriggerDB.KEY_ID));

				String trigDesc = db.getTriggerDescription(trigId);
				BlackoutDesc conf = new BlackoutDesc();

				if (!conf.loadString(trigDesc)) {
					continue;
				}
				SimpleTime start = conf.getRangeStart();
				SimpleTime end = conf.getRangeEnd();
				SimpleTime now = new SimpleTime();
				if (!start.isAfter(now) && end.isAfter(now)) {
					canRunNow = false;
				}

			} while (c.moveToNext());
		}
		c.close();
		db.close();
		TriggerInit.initTriggers(context);
		if (canRunNow)
			startRunning(context);
	}

	public static void stop(Context context) {

		TriggerDB db = new TriggerDB(context);
		db.open();
		boolean runningNow = true;
		Cursor c = db.getAllTriggers();
		if (c.moveToFirst()) {
			do {
				int trigId = c
						.getInt(c.getColumnIndexOrThrow(TriggerDB.KEY_ID));

				String trigDesc = db.getTriggerDescription(trigId);
				BlackoutDesc conf = new BlackoutDesc();

				if (!conf.loadString(trigDesc)) {
					continue;
				}
				SimpleTime start = conf.getRangeStart();
				SimpleTime end = conf.getRangeEnd();
				SimpleTime now = new SimpleTime();
				if (!start.isAfter(now) && !end.isBefore(now)) {
					runningNow = false;
				}
				new Blackout().stopTrigger(context, trigId,
						db.getTriggerDescription(trigId));

			} while (c.moveToNext());
		}
		c.close();
		db.close();
		// TriggerInit.initTriggers(context);
		if (runningNow)
			stopRunning(context, false);
		// LogProbe.close(context);
	}
}
