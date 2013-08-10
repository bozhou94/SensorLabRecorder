package edu.dartmouth.cs.audiorecorder;

import java.util.ArrayList;

import edu.dartmouth.cs.mltoolkit.processing.AudioFeatureExtraction;
import edu.dartmouth.cs.mltoolkit.processing.AudioInference;

public class ArrayStorage {

	private int row, col;

	private int framePeriod;
	private final int nmfcc = 20;
	// private int lefr;
	private int voicedFrameNum = 0;
	// private double zcr_m, zcr_v, rms_m, rms_s, rms_threshold;
	private double rate = -1;
	private short[] data;
	private float[] rdata;
	private double[] fdata;
	private double[][] tdata;
	private short[][] data_buffer;
	private double[][] fdata_buffer;
	// private double[] rms;
	private double[] zcr;
	private ArrayList<Double> pitch;
	private double[] featureset;
	private double[][] tdata_buffer;
	private double[] teagerFeature;
	private int[] teager_index = new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
			11, 12, 13, 14, 15, 16, 17 };;
	private ArrayList<double[]> featureList;
	private AudioFeatureExtraction features;
	private double[] audioFrameFeature;
	private AudioData audioFromQueueData;
	private CircularBufferFeatExtractionInference<AudioData> cirBuffer;
	
	public void initiate(int frameSize, int windowSize, int framePeriod, CircularBufferFeatExtractionInference<AudioData> cirBuffer) {
		this.framePeriod = framePeriod;
		features = new AudioFeatureExtraction(frameSize, windowSize, 24,
				20, 8000);
		audioFrameFeature = new double[features.getFrame_feature_size()];
		row = features.getWindow_length();
		col = features.getFrame_length();
		data_buffer = new short[row][col];
		fdata_buffer = new double[row][col];
		// rms = new double[row];
		zcr = new double[row];
		// int[] teager_index = new int[]{2,6,7,8,9,10,11,17};
		tdata_buffer = new double[row][teager_index.length * col];
		teagerFeature = new double[teager_index.length];
		featureset = new double[teager_index.length + nmfcc - 1 + 5];
		// features for voice detection
		pitch = new ArrayList<Double>();
		featureList = new ArrayList<double[]>();
		data = new short[framePeriod];
		rdata = new float[framePeriod];
		fdata = new double[framePeriod];
		tdata = new double[teager_index.length][framePeriod];
		this.cirBuffer = cirBuffer;
	}
	
	public String run() {
		// double time = 0, time1 = 0, time2 = 0, time3 = 0, time4 = 0;

		audioFromQueueData = cirBuffer.deleteAndHandleData();

		/* data length is in dataSize */
		// int dataSize = audioFromQueueData.mSize;

		if (audioFromQueueData.mSize < framePeriod)
			return null;

		// time = System.currentTimeMillis();
		/* data to process is in data */

		data = audioFromQueueData.mData;

		// sampling error

		// detecting sound
		// double f_rms = features.rms(data);
		if (features.rms(data) < 250) {
			return "silence";
			// time1 = System.currentTimeMillis();
			// Log.d(TAG, "slience with rms:" + f_rms + "time "
			// + (time1 - time) / 1000);
		}

		// System.arraycopy(audiodata.mData, 0, data, 0,
		// framePeriod);

		voicedFrameNum = 0;
		pitch.clear();
		featureList.clear();

		// setActivityText(String.format("dataSize %d shorts %d",
		// dataSize, data.length));

		// detecting voice
		for (int i = 0; i < row; i++)
			// {
			System.arraycopy(data, i * col, data_buffer[i], 0, col);

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
					// time2 = System.currentTimeMillis();
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
		// time3 = System.currentTimeMillis();
		// Log.d(TAG, "feature time " + (time3 - time2) / 1000);

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
		}// time4 = System.currentTimeMillis();
		// Log.d(TAG,this + "pitch features " + c + " " +
		// Arrays.toString(pitch.toArray()));
		// Log.d(TAG, "Inf time " + (time4 - time3) / 1000);
		// Log.d(TAG, "total time " + (time4 - time) / 1000);
		if (s > c / 2)
			return String.format("stressed");
		else
			return String.format("not stressed");
	}
}
