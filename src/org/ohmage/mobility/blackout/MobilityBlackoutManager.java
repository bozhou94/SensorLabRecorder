package org.ohmage.mobility.blackout;

import edu.dartmouth.cs.audiorecorder.SensorPreferenceActivity;
import android.content.Context;
import android.content.SharedPreferences;

public class MobilityBlackoutManager
{
	private static boolean running = false;
	private static boolean initialized = false;
	private static SharedPreferences settings;
//	private static Editor editor;
	public static void initializeMobilityBlackouts(Context context)
	{
		settings = context.getSharedPreferences(SensorPreferenceActivity.STRESSSENSE, Context.MODE_PRIVATE);
		running = settings.getBoolean(SensorPreferenceActivity.IS_ON, false); 
//		editor = settings.edit();
		
	}	
	
	public static void toggleMobility(Context context)
	{
		if (!initialized)
			initializeMobilityBlackouts(context);
		if (running)
			SensorPreferenceActivity.stop(context);
//			MobilityInterface.stopMobility(context);
		else
			SensorPreferenceActivity.start(context);
//			MobilityInterface.startMobility(context);
		running = !running;
	}
}
