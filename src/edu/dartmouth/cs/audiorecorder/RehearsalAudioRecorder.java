package edu.dartmouth.cs.audiorecorder;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.io.*;

import org.ohmage.probemanager.ProbeBuilder;
import org.ohmage.probemanager.StressSenseProbeWriter;

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

	public static final boolean RECORDING_UNCOMPRESSED = true;
	public static final boolean RECORDING_COMPRESSED = false;
	private static final String TAG = "RehearsalAudioRecorder";

	// Recorder used for uncompressed recording
	private AudioRecord aRecorder = null;

	// Output file path
	private String fPath = null;

	// Recorder state; see State
	private State state;

	// File writer
	private DataOutputStream mDataOutput;

	// Number of channels, sample rate, sample size(size in bits), buffer size,
	// audio source, sample size(see AudioFormat)
	private short nChannels;
	private int sRate;
	private short bSamples;
	private int bufferSize;
	private int aSource;
	private int aFormat;
	private int aChannelConfig;

	private boolean mWriteToFile;

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

	// Number of bytes written to file after header(only in uncompressed mode)
	// after stop() is called, this size is written to the header/data chunk in
	// the wave file
	private int payloadSize;

	private CircularBufferFeatExtractionInference<AudioData> cirBuffer;
	private AudioProcessing mAudioProcessingThread1;
	private AudioProcessing mAudioProcessingThread2;

	// Used for uploading the information
	private static StressSenseProbeWriter probeWriter;

	// Used for analytics
	private String prevStatus;
	private String prevTime;

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
				if (mWriteToFile) {
					try {
						// Write buffer to file
						for (int i = 0; i < numRead; ++i) {
							mDataOutput.writeShort(Short
									.reverseBytes(buffer[i]));
						}
						mDataOutput.flush();
					} catch (IOException e) {
						Log.e(TAG,
								"Error occured in updateListener, recording is aborted");
						stop();
						return;
					}
				}
				cirBuffer.insert(new AudioData(buffer, numRead));
				payloadSize += numRead;
			} else {
				Log.e(TAG,
						"Error occured in updateListener, recording is aborted");
				stop();
			}
		}

	}

	
	private class AudioReadingThread extends Thread {
		
		AudioRecord record;
		public AudioReadingThread(AudioRecord recorder) {
			record = recorder;
		}
		
		@Override
		public void run() {
			int numRead = record.read(buffer, 0, buffer.length); ;
			if (numRead != AudioRecord.ERROR_INVALID_OPERATION
					&& numRead != AudioRecord.ERROR_BAD_VALUE) {
				if (mWriteToFile) {
					try {
						// Write buffer to file
						for (int i = 0; i < numRead; ++i) {
							mDataOutput.writeShort(Short
									.reverseBytes(buffer[i]));
						}
						mDataOutput.flush();
					} catch (IOException e) {
						Log.e(TAG,
								"Error occured in updateListener, recording is aborted");
						stop();
						return;
					}
				}
				cirBuffer.insert(new AudioData(buffer, numRead));
				payloadSize += numRead;
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
		
		int pos = 0;
		AudioReadingThread tasks[] = new AudioReadingThread[5];
		
		@Override
		public void onPeriodicNotification(AudioRecord recorder) {
			//new AudioReadingTask().execute(); // previously contents of
												// AudioReadingTask were here
			
			if (tasks[pos] == null || tasks[pos].getState() != Thread.State.RUNNABLE) {
				tasks[pos] = new AudioReadingThread(recorder);
				tasks[pos].start();
				}
			pos = (pos > 3) ? 0 : (pos + 1);
		}

		@Override
		public void onMarkerReached(AudioRecord recorder) {
			// NOT USED
		}
	};

	public RehearsalAudioRecorder(StressSenseProbeWriter probewriter,
			int audioSource, int sampleRate, int channelConfig, int audioFormat) {
		this(probewriter, audioSource, sampleRate, channelConfig, audioFormat,
				false);
	}

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
	public RehearsalAudioRecorder(StressSenseProbeWriter probewriter,
			int audioSource, int sampleRate, int channelConfig,
			int audioFormat, boolean writeToFile) {
		mWriteToFile = writeToFile;
		aChannelConfig = channelConfig;

		try {
			if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) {
				bSamples = 16;
			} else {
				bSamples = 8;
			}

			if (channelConfig == AudioFormat.CHANNEL_IN_MONO) {
				nChannels = 1;
			} else {
				nChannels = 2;
			}

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

			aRecorder = new AudioRecord(audioSource, sampleRate, channelConfig,
					audioFormat, bufferSize);
			if (aRecorder.getState() != AudioRecord.STATE_INITIALIZED)
				throw new Exception("AudioRecord initialization failed");
			aRecorder.setRecordPositionUpdateListener(updateListener);
			aRecorder.setPositionNotificationPeriod(framePeriod);
			fPath = null;
			state = State.INITIALIZING;
			cirBuffer = new CircularBufferFeatExtractionInference<AudioData>(
					null, 100);

			probeWriter = probewriter;

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
	 * Sets output file path, call directly after construction/reset.
	 * 
	 * @param output
	 *            file path
	 * 
	 */
	public void setOutputFile(String argPath) {
		try {
			if (state == State.INITIALIZING) {
				fPath = argPath;
			}
		} catch (Exception e) {
			if (e.getMessage() != null) {
				Log.e(TAG, e.getMessage());
			} else {
				Log.e(TAG, "Unknown error occured while setting output path");
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
					// write file header
					if (mWriteToFile && fPath != null) {
						mDataOutput = new DataOutputStream(
								new BufferedOutputStream(new FileOutputStream(
										fPath)));
						// Set file length to 0, to prevent unexpected behavior
						// in case the file already existed
						mDataOutput.writeBytes("RIFF");
						mDataOutput.writeInt(0); // Final file size not known
						// yet,
						// write 0
						mDataOutput.writeBytes("WAVE");
						mDataOutput.writeBytes("fmt ");
						/* Sub-chunk size, 16 for PCM */
						mDataOutput.writeInt(Integer.reverseBytes(16));
						/* AudioFormat, 1 for PCM */
						mDataOutput.writeShort(Short.reverseBytes((short) 1));
						/* Number of channels, 1 formono, 2 for stereo */
						mDataOutput.writeShort(Short.reverseBytes(nChannels));
						// Sample rate
						mDataOutput.writeInt(Integer.reverseBytes(sRate));
						// Byte rate
						mDataOutput.writeInt(Integer.reverseBytes(sRate
								* bSamples * nChannels / 8));
						// Block align
						mDataOutput
								.writeShort(Short
										.reverseBytes((short) (nChannels
												* bSamples / 8)));
						// Bits per sample
						mDataOutput.writeShort(Short.reverseBytes(bSamples));
						mDataOutput.writeBytes("data");
						// Data chunk size not known yet, write 0
						mDataOutput.writeInt(0);
					}

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
		if (state == State.RECORDING) {
			stop();
		} else {
			if (state == State.READY) {
				try {
					if (mWriteToFile) {
						mDataOutput.close(); // Remove prepared file
					}
				} catch (IOException e) {
					Log.e(TAG,
							"I/O exception occured while closing output file");
				}
				if (mWriteToFile) {
					(new File(fPath)).delete();
				}
			}
		}

		if (aRecorder != null) {
			aRecorder.release();
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
				fPath = null; // Reset file path
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
			mAudioProcessingThread1 = new AudioProcessing(cirBuffer);
			mAudioProcessingThread1.start();
			mAudioProcessingThread2 = new AudioProcessing(cirBuffer);
			mAudioProcessingThread2.start();
			payloadSize = 0;
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
			aRecorder.stop();

			if (mWriteToFile) {
				try {
					mDataOutput.flush();
					mDataOutput.close();
					int sizeToWrite = payloadSize * 2;
					RandomAccessFile fWriter = new RandomAccessFile(fPath, "rw");
					fWriter.seek(4); // Write size to RIFF header
					fWriter.writeInt(Integer.reverseBytes(36 + sizeToWrite));

					fWriter.seek(40); // Write size to Subchunk2Size field
					fWriter.writeInt(Integer.reverseBytes(sizeToWrite));

					fWriter.close();
				} catch (IOException e) {
					Log.e(TAG,
							"I/O exception occured while closing output file");
					state = State.ERROR;
				}
			}

			state = State.STOPPED;
		} else {
			Log.e(TAG, "stop() called on illegal state");
			state = State.ERROR;
		}
	}

	private class AudioData {
		public short[] mData;
		public int mSize;

		public AudioData(short[] data, int size) {
			this.mData = data;
			this.mSize = size;
		}
	}

	private AudioData audioFromQueueData;

	/**
	 * PROCESSING DONE IN THIS THREAD
	 */
	private class AudioProcessing extends Thread {

		private CircularBufferFeatExtractionInference<AudioData> obj;
		private AudioFeatureExtraction features;
		private double[] audioFrameFeature;

		public AudioProcessing(
				CircularBufferFeatExtractionInference<AudioData> obj) {
			this.obj = obj;
			features = new AudioFeatureExtraction(frameSize, windowSize, 24,
					20, 8000);
			audioFrameFeature = new double[features.getFrame_feature_size()];
		}

		@Override
		public void run() {
			short[] data = new short[framePeriod];
			float[] rdata = new float[framePeriod];
			double[] fdata = new double[framePeriod];
			final int row = features.getWindow_length();
			final int col = features.getFrame_length();
			short[][] data_buffer = new short[row][col];
			double[][] fdata_buffer = new double[row][col];
			double[] rms = new double[row];
			double[] zcr = new double[row];
			int[] teager_index = new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
					12, 13, 14, 15, 16, 17 };//
			// int[] teager_index = new int[]{2,6,7,8,9,10,11,17};
			final int nmfcc = 20;
			double[][] tdata = new double[teager_index.length][framePeriod];
			double[][] tdata_buffer = new double[row][teager_index.length * col];
			double[] teagerFeature = new double[teager_index.length];
			double[] featureset = new double[teager_index.length + nmfcc - 1
					+ 5];
			// features for voice detection
			double zcr_m, zcr_v, rms_m, rms_s, rms_threshold;
			ArrayList<Double> pitch = new ArrayList<Double>();
			ArrayList<double[]> featureList = new ArrayList<double[]>();
			int lefr;
			double rate = -1;
			int voicedFrameNum = 0;
			// features = new AudioFeatureExtraction(col, row, 20, 8000);
			// test only
			short[] test = new short[framePeriod];

			try {
				BufferedReader f = new BufferedReader(new FileReader(new File(
						"/sdcard/test")));
				String[] d = f.readLine().split(",");
				for (int i = 0; i < framePeriod; i++) {
					test[i] = Short.parseShort(d[i]);
					// if(i<5) Log.d(TAG, String.format("data:%d",test[i]));
				}
				f.close();
			} catch (IOException e) {
				Log.d(TAG, "error" + e.getMessage());
			}

			while (true) {
				double time = 0, time1 = 0, time2 = 0, time3 = 0, time4 = 0;
				audioFromQueueData = obj.deleteAndHandleData();
				time = System.currentTimeMillis();
				/* data to process is in data */
				data = audioFromQueueData.mData;
				/* data length is in dataSize */
				int dataSize = audioFromQueueData.mSize;
				if (dataSize < framePeriod)
					continue;
				// System.arraycopy(audiodata.mData, 0, data, 0,
				// framePeriod);
				voicedFrameNum = 0;
				pitch.clear();
				featureList.clear();

				// setActivityText(String.format("dataSize %d shorts %d",
				// dataSize, data.length));

				// sampling error

				// detecting sound
				double f_rms = features.rms(data);
				if (f_rms < 250) {
					setActivityText("silence");
					time1 = System.currentTimeMillis();
					Log.d(TAG, "slience with rms:" + f_rms + "time "
							+ (time1 - time) / 1000);
					continue;
				}

				// detecting voice
				for (int i = 0; i < row; i++) {
					System.arraycopy(data, i * col, data_buffer[i], 0, col);
					rms[i] = features.rms(data_buffer[i]);
					zcr[i] = features.zcr(data_buffer[i]);
				}

				zcr_m = features.mean(zcr);
				rms_m = features.mean(rms);
				zcr_v = features.var(zcr, zcr_m);
				rms_s = Math.sqrt(features.var(rms, rms_m)) / rms_m;
				rms_threshold = rms_m * 0.5;
				lefr = 0;

				for (double i : rms) {
					if (i < rms_threshold)
						lefr++;
				}

				if (AudioInference.tree(zcr_v, zcr_m, rms_s, lefr) == 0) {
					// setActivityText("noise");
					time2 = System.currentTimeMillis();
					Log.d(TAG, "noise" + "time " + (time2 - time1) / 1000);
					// continue;
				}

				fdata[0] = data[0];
				for (int i = 1; i < framePeriod; i++) {
					fdata[i] = data[i] - 0.97 * data[i - 1];
				}

				for (int i = 0; i < row; i++) {
					System.arraycopy(fdata, i * col, fdata_buffer[i], 0, col);
					zcr[i] = features.zcr(fdata_buffer[i]);
					if (zcr[i] > 120)
						continue;
					int voiced = features.getFrameFeat(fdata_buffer[i],
							audioFrameFeature);

					if (voiced == 1) {
						pitch.add(audioFrameFeature[21]);
						voicedFrameNum++;
						if (voicedFrameNum == 1) {
							for (int j = 0; j < framePeriod; j++) {
								rdata[j] = data[j];
							}
							time2 = System.currentTimeMillis();
							features.conv(data, framePeriod, teager_index,
									tdata);
							features.teo(tdata, framePeriod, tdata_buffer);
							rate = features.getEnrate(rdata, framePeriod, 8000);

						}

						features.getTeo(tdata_buffer[i], teager_index.length,
								col, teagerFeature);
						System.arraycopy(teagerFeature, 0, featureset, 0,
								teager_index.length);
						System.arraycopy(audioFrameFeature, 0, featureset,
								teager_index.length, nmfcc + 2);
						featureList.add(featureset.clone());
					}
				}

				double[] pitchFeature = new double[2];
				features.var(pitch, pitchFeature);
				time3 = System.currentTimeMillis();
				Log.d(TAG, "feature time " + (time3 - time2) / 1000);

				int c = 0;
				int s = 0;
				for (double[] f : featureList) {
					f[teager_index.length + nmfcc + 1] = pitchFeature[0];
					f[teager_index.length + nmfcc + 2] = pitchFeature[1];
					f[teager_index.length + nmfcc + 3] = rate;
					s += AudioInference.stressInference(f);
					// Log.d(TAG,this + "voiced features " + c + " " +
					// Arrays.toString(f));
					c++;
				}
				Log.d(TAG, this + "voiced features " + c + " " + s);// +
																	// " "+
																	// Arrays.toString(featureList.get(0)));
				time4 = System.currentTimeMillis();
				// Log.d(TAG,this + "pitch features " + c + " " +
				// Arrays.toString(pitch.toArray()));
				Log.d(TAG, "Inf time " + (time4 - time3) / 1000);
				Log.d(TAG, "total time " + (time4 - time) / 1000);

				if (s > c / 2)
					setActivityText(String.format("stressed"));
				else
					setActivityText(String.format("not stressed"));
			}
		}

	}

	/**
	 * Notifies the handler of the analytic activity of the current status
	 */
	public synchronized void setActivityText(final String text) {

		// Displays the last 10 minutes to the user
		String curTime = new SimpleDateFormat("h:mm a").format(Calendar
				.getInstance().getTime());
		if (prevTime == null || !prevTime.equals(curTime)) {
			AudioRecorderService.changeHistory.addFirst(curTime + ": " + text);
			if (AudioRecorderService.changeHistory.size() > 10)
				AudioRecorderService.changeHistory.removeLast();
			prevTime = curTime;
		}
		
		// Displays all the mode changes to probing
		if (prevStatus == null || !prevStatus.equals(text)) {
			
			if (probeWriter != null) {
				ProbeBuilder probe = new ProbeBuilder();
				probe.withTimestamp(new SimpleDateFormat(
						"yyyy-MM-dd'T'HH:mm'Z'").format(new Date()));
				probeWriter.write(probe, text);
			}
			
			prevStatus = text;
		}
		
		// Updates analytic display of current mode
		Handler handler = StressActivity.getHandler();
		if (null != handler) {
			Message m = new Message();
			Bundle data = new Bundle();
			data.putString(AudioRecorderService.AUDIORECORDER_NEWTEXT_CONTENT,
					text);
			m.setData(data);
			handler.sendMessage(m);
		}
	}

}
