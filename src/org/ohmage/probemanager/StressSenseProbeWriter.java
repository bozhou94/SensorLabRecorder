package org.ohmage.probemanager;

import java.util.Arrays;

import android.content.Context;
import android.os.RemoteException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class StressSenseProbeWriter extends ProbeWriter {

	private static final String OBSERVER_ID = "edu.dartmouth.cs.audiorecorder.summary";
	private static final int OBSERVER_VERSION = 2013072200;
	private static final String STREAM_SIMPLE = "stresssense";
    private static final int STREAM_SIMPLE_VERSION = 2013072200;

	public StressSenseProbeWriter(Context context) {
		super(context);
	}

	public void write(ProbeBuilder probe, String mode, String stressage /*, short[] audio*/) {
		
		try {
			probe.setObserver(OBSERVER_ID, OBSERVER_VERSION);
			probe.setStream(STREAM_SIMPLE, STREAM_SIMPLE_VERSION);
			JSONObject data = new JSONObject();
			JSONArray list = new JSONArray(Arrays.asList(mode, stressage));
			data.put("mode", list);
			/*
			JSONArray ja = new JSONArray();
			for (short i : audio) {
				JSONObject jo = new JSONObject();
				jo.put("raw", i);
				ja.put(jo);
			}
			data.put("audio_data", ja);*/
			probe.setData(data.toString()).write(this);
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
}
