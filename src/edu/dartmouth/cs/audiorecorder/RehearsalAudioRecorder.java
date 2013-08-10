package edu.dartmouth.cs.audiorecorder;

import java.text.SimpleDateFormat;
import java.util.*;

import org.ohmage.probemanager.ProbeBuilder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import edu.dartmouth.cs.audiorecorder.analytics.StressActivity;
import edu.dartmouth.cs.mltoolkit.processing.*;

public class RehearsalAudioRecorder {
	/**
	 * INITIALIZING : recorder is initializing; READY : recorder has been
	 * initialized, recorder not yet started RECORDING : recording ERROR :
	 * reconstruction needed STOPPED: reset needed
	 */
	public enum State {
		INITIALIZING, READY, RECORDING, ERROR, STOPPED
	};

	private static final String TAG = "RehearsalAudioRecorder";

	// Recorder used for uncompressed recording
	private AudioRecord aRecorder = null;

	// Recorder state; see State
	private State state;

	// Number of channels, sample rate, sample size(size in bits), buffer size,
	// audio source, sample size(see AudioFormat)
	private short nChannels;
	private int sRate;
	private short bSamples;
	private int bufferSize;
	private int aSource;
	private int aFormat;
	private int aChannelConfig;

	private int frameSize;
	private int windowSize;

	// = new double[af.getFrame_feature_size()];
	// double [] audioWindowFeature;// = new
	// double[af.getWindow_feature_size()];

	// Number of frames written to file on each output(only in uncompressed
	// mode)
	private int framePeriod;

	// Buffer for output(only in uncompressed mode)
	private short[] buffer;

	private CircularBufferFeatExtractionInference<AudioData> cirBuffer;
	private AudioProcessing mAudioProcessingThread1;
	private AudioProcessing mAudioProcessingThread2;
	
	// Used for sampling at lower rates
	private static final int rateMultiplier = 0;

	/**
	 * 
	 * Returns the state of the recorder in a RehearsalAudioRecord.State typed
	 * object. Useful, as no exceptions are thrown.
	 * 
	 * @return recorder state
	 */
	public State getState() {
		return state;
	}

	private class AudioReadingTask extends AsyncTask<Void, Void, Integer> {

		@Override
		protected Integer doInBackground(Void... arg0) {
			return aRecorder.read(buffer, 0, buffer.length); // This causes
																// application
																// to be slow if
																// it's on the
																// main thread
		}

		@Override
		protected void onPostExecute(Integer result) {
			int numRead = result;
			if (numRead != AudioRecord.ERROR_INVALID_OPERATION
					&& numRead != AudioRecord.ERROR_BAD_VALUE) {
				cirBuffer.insert(new AudioData(buffer, numRead));
			} else {
				Log.e(TAG,
						"Error occured in updateListener, recording is aborted");
				stop();
			}
		}

	}

	/*
	 * 
	 * Method used for recording.
	 */
	private AudioRecord.OnRecordPositionUpdateListener updateListener = new AudioRecord.OnRecordPositionUpdateListener() {

		// private AudioReadingTask[] tasks = new AudioReadingTask[5];
		// private int count;

		@Override
		public void onPeriodicNotification(AudioRecord recorder) {
			//if (count == rateMultiplier) {
				new AudioReadingTask().execute();
				//count = 0;
			//} else count++;
			
		}

		@Override
		public void onMarkerReached(AudioRecord recorder) {
			// NOT USED
		}
	};

	/**
	 * 
	 * 
	 * Default constructor
	 * 
	 * Instantiates a new recorder, in case of compressed recording the
	 * parameters can be left as 0. In case of errors, no exception is thrown,
	 * but the state is set to ERROR
	 * 
	 */
	public RehearsalAudioRecorder(int audioSource, int sampleRate,
			int channelConfig, int audioFormat) {
		aChannelConfig = channelConfig;

		try {

			bSamples = (short) ((audioFormat == AudioFormat.ENCODING_PCM_16BIT) ? 16
					: 8);
			nChannels = (short) ((channelConfig == AudioFormat.CHANNEL_IN_MONO) ? 1
					: 2);

			aSource = audioSource;
			sRate = sampleRate;
			aFormat = audioFormat;

			if (sampleRate < 11000) {
				// 40 256 frame
				frameSize = 256;
				windowSize = 40;
				framePeriod = frameSize * windowSize;
			} else if (sampleRate < 22050) {
				framePeriod = 2048;
			} else if (sampleRate < 44100) {
				framePeriod = 4096;
			} else {
				framePeriod = 8192;
			}
			bufferSize = (framePeriod * 2 * bSamples * nChannels) / Short.SIZE;

			/*
			 * Check to make sure buffer size is not smaller than the smallest
			 * allowed one
			 */

			if (bufferSize < AudioRecord.getMinBufferSize(sampleRate,
					channelConfig, audioFormat)) {
				bufferSize = AudioRecord.getMinBufferSize(sampleRate,
						channelConfig, audioFormat) * 2;
				// Set frame period and timer interval accordingly
				framePeriod = bufferSize / (2 * bSamples * nChannels / 8);
				Log.w(TAG,
						"Increasing buffer size to "
								+ Integer.toString(bufferSize));
			}

			cirBuffer = new CircularBufferFeatExtractionInference<AudioData>(
					null, 100);

		} catch (Exception e) {
			if (e.getMessage() != null) {
				Log.e(TAG, e.getMessage());
			} else {
				Log.e(TAG, "Unknown error occured while initializing recording");
			}
			state = State.ERROR;
		}
	}

	/**
	 * 
	 * Prepares the recorder for recording, in case the recorder is not in the
	 * INITIALIZING state and the file path was not set the recorder is set to
	 * the ERROR state, which makes a reconstruction necessary. In case
	 * uncompressed recording is toggled, the header of the wave file is
	 * written. In case of an exception, the state is changed to ERROR
	 * 
	 */
	public void prepare() {
		try {
			if (state == State.INITIALIZING) {
				if ((aRecorder.getState() == AudioRecord.STATE_INITIALIZED)) {
					// buffer = new short[bufferSize];
					buffer = new short[framePeriod * bSamples / 16 * nChannels];
					state = State.READY;
				} else {
					Log.e(TAG,
							"prepare() method called on uninitialized recorder");
					state = State.ERROR;
				}
			} else {
				Log.e(TAG, "prepare() method called on illegal state");
				release();
				state = State.ERROR;
			}
		} catch (Exception e) {
			if (e.getMessage() != null) {
				Log.e(TAG, e.getMessage());
			} else {
				Log.e(TAG, "Unknown error occured in prepare()");
			}
			state = State.ERROR;
		}
	}

	/**
	 * 
	 * 
	 * Releases the resources associated with this class, and removes the
	 * unnecessary files, when necessary
	 * 
	 */
	public void release() {
		if (state == State.RECORDING)
			stop();

		if (aRecorder != null) {
			aRecorder.release();
			aRecorder = null;
		}
	}

	/**
	 * 
	 * 
	 * Resets the recorder to the INITIALIZING state, as if it was just created.
	 * In case the class was in RECORDING state, the recording is stopped. In
	 * case of exceptions the class is set to the ERROR state.
	 * 
	 */
	public void reset() {
		try {
			if (state != State.ERROR) {
				release();
				aRecorder = new AudioRecord(aSource, sRate, aChannelConfig,
						aFormat, bufferSize);
				aRecorder.setRecordPositionUpdateListener(updateListener);
				aRecorder.setPositionNotificationPeriod(framePeriod);
				state = State.INITIALIZING;
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
			state = State.ERROR;
		}
	}

	/**
	 * 
	 * 
	 * Starts the recording, and sets the state to RECORDING. Call after
	 * prepare().
	 * 
	 */
	public void start() {
		if (state == State.READY) {
			mAudioProcessingThread1 = new AudioProcessing(AudioRecorderService.Storage1);
			mAudioProcessingThread1.start();
			mAudioProcessingThread2 = new AudioProcessing(AudioRecorderService.Storage2);
			mAudioProcessingThread2.start();
			aRecorder.startRecording();
			aRecorder.read(buffer, 0, buffer.length);
			state = State.RECORDING;
		} else {
			Log.e(TAG, "start() called on illegal state");
			state = State.ERROR;
		}
	}

	/**
	 * 
	 * Stops the recording, and sets the state to STOPPED. Only the first call
	 * to stop() has effects. In case of further usage, a reset is needed. Also
	 * finalizes the wave file in case of uncompressed recording.
	 * 
	 */
	public void stop() {
		if (state == State.STOPPED) {
			return;
		}
		if (state == State.RECORDING) {
			mAudioProcessingThread1.stopRunning();
			mAudioProcessingThread1 = null;
			mAudioProcessingThread2.stopRunning();
			mAudioProcessingThread2 = null;
			aRecorder.stop();
			state = State.STOPPED;
		} else {
			Log.e(TAG, "stop() called on illegal state");
			state = State.ERROR;
		}
	}
	
	/**
	 * PROCESSING DONE IN THIS THREAD
	 */
	private class AudioProcessing extends Thread {

		private volatile boolean running;
		private ArrayStorage store;

		public AudioProcessing(ArrayStorage store) {
			running = true;
			store.initiate(frameSize, windowSize, framePeriod, cirBuffer);
			this.store = store;
			// features = new AudioFeatureExtraction(col, row, 20, 8000);
		}

		@Override
		public void run() {

			while (running) {
				
				String result = store.run();
				if (result != null) 
					setActivityText(result);
			}
		}

		public void stopRunning() {
			running = false;
		}
	}

	/**
	 * Notifies the handler of the analytic activity of the current status
	 */
	public static void setActivityText(final String text) {

		String prevStatus = AudioRecorderService.text;

		if (text.equals("stressed"))
			AudioRecorderService.curTotals[0]++;
		else if (text.equals("not stressed"))
			AudioRecorderService.curTotals[1]++;
		else if (text.equals("silence"))
			AudioRecorderService.curTotals[2]++;

		if (AudioRecorderService.probeWriter != null) {
			ProbeBuilder probe = new ProbeBuilder();
			probe.withTimestamp(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'")
					.format(new Date()));
			AudioRecorderService.probeWriter.write(probe, text);
		}
		if (!prevStatus.equals(text)) {
			AudioRecorderService.text = text;
			Handler handler = StressActivity.getHandler();
			if (null != handler) {
				Message m = new Message();
				Bundle data = new Bundle();
				data.putString(
						AudioRecorderService.AUDIORECORDER_NEWTEXT_CONTENT,
						text);
				m.setData(data);
				handler.sendMessage(m);
			}
		}
	}
}
