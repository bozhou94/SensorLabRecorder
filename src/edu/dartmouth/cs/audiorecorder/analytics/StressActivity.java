package edu.dartmouth.cs.audiorecorder.analytics;

import java.text.DecimalFormat;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

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
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This is the analytic portion of StressSense.
 * 
 * Displays: Status of the current processed audio History of the previous
 * processed audio statuses
 */
public class StressActivity extends Activity {

	// Layout Components
	private TextView mTvGenericText;
	private TextView mStressed;
	private TextView mNStressed;
	private TextView mSilence;
	private TextView mTimeText;

	// Used for status writing
	private static Handler sMessageHandler;

	// BroadcastReceiver for getting On/Off signals from the service
	private AudioRecorderStatusRecevier mAudioRecorderStatusReceiver;
	private SamplePercentageReceiver mSampleReceiver;
	
	// Used for charts
	private GraphicalView mChartView;
	private XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer();
	private XYMultipleSeriesDataset mDataset = new XYMultipleSeriesDataset();
	private XYSeries mCurrentSeries;
	private XYSeriesRenderer mCurrentRenderer;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_analytic);

		mAudioRecorderStatusReceiver = new AudioRecorderStatusRecevier();
		mSampleReceiver = new SamplePercentageReceiver();

		mTvGenericText = (TextView) findViewById(R.id.tvStatus);
		mTimeText = (TextView) findViewById(R.id.time);
		mTvGenericText.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				Intent intent = new Intent(StressActivity.this, AnalyticHistory.class);
				startActivity(intent);
			}
			
		});
		
		mStressed = (TextView) findViewById(R.id.recent_stress_stress);
		mNStressed = (TextView) findViewById(R.id.recent_stress_not);
		mSilence = (TextView) findViewById(R.id.recent_stress_none);
		
		//Graph rendering
		mRenderer.setAxisTitleTextSize(16);
	    mRenderer.setChartTitleTextSize(20);
	    mRenderer.setLabelsTextSize(15);
	    mRenderer.setShowLegend(false);
	    mRenderer.setMargins(new int[] { 20, 0, 10, 0 });
	    mRenderer.setPointSize(5);
	    
	    String seriesTitle = "";
        // create a new series of data
        XYSeries series = new XYSeries(seriesTitle);
        mDataset.addSeries(series);
        mCurrentSeries = series;
        
        // create a new renderer for the new series
        XYSeriesRenderer renderer = new XYSeriesRenderer();
        mRenderer.addSeriesRenderer(renderer);
      
        // set some renderer properties
        renderer.setPointStyle(PointStyle.CIRCLE);
        renderer.setFillPoints(true);
        renderer.setDisplayChartValues(true);
        renderer.setDisplayChartValuesDistance(10);
        mCurrentRenderer = renderer;
	}

	@Override
	public void onStart() {
		super.onStart();
		registerReceiver(mAudioRecorderStatusReceiver, new IntentFilter(
				AudioRecorderService.AUDIORECORDER_ON));
		registerReceiver(mAudioRecorderStatusReceiver, new IntentFilter(
				AudioRecorderService.AUDIORECORDER_OFF));
		registerReceiver(mSampleReceiver, new IntentFilter(
				AudioRecorderService.CALCULATE_PERCENTAGE));
		sMessageHandler = mHandler;
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
				SensorPreferenceActivity.IS_ON, false))
			mTvGenericText.setCompoundDrawablesWithIntrinsicBounds(
					R.drawable.mic_on, 0, 0, 0);
		calculatePercentages();
	}
	
	@Override
	  protected void onResume() {
	    super.onResume();
	    if (mChartView == null) {
	      LinearLayout layout = (LinearLayout) findViewById(R.id.chart);
	      mChartView = ChartFactory.getLineChartView(this, mDataset, mRenderer);
	      layout.addView(mChartView, new LayoutParams(LayoutParams.WRAP_CONTENT,
	          LayoutParams.WRAP_CONTENT));
	    } else {
	      mChartView.repaint();
	    }
	  }

	@Override
	public void onStop() {
		super.onStop();
		unregisterReceiver(mAudioRecorderStatusReceiver);
		unregisterReceiver(mSampleReceiver);
		sMessageHandler = null;
		mTvGenericText.setCompoundDrawablesWithIntrinsicBounds(
				R.drawable.mic_off, 0, 0, 0);
		mTvGenericText.setText(AudioRecorderService.text);
	}

	/**
	 * Inputs the calculations of the samples taken to the menu
	 */
	private void calculatePercentages() {
		int numSSamples = PreferenceManager.getDefaultSharedPreferences(this)
				.getInt(AudioRecorderService.PERCENT_STRESSED, 0);
		int numNSamples = PreferenceManager.getDefaultSharedPreferences(this)
				.getInt(AudioRecorderService.PERCENT_NSTRESSED, 0);
		int numSiSamples = PreferenceManager.getDefaultSharedPreferences(this)
				.getInt(AudioRecorderService.PERCENT_SILENT, 0);
		int total = numSSamples + numNSamples + numSiSamples;
		if (total > 0) {
			mStressed.setText(new DecimalFormat("#.##").format(100.0 * numSSamples / total) + "%");
			mNStressed.setText(new DecimalFormat("#.##").format(100.0 * numNSamples / total) + "%");
			mSilence.setText(new DecimalFormat("#.##").format(100.0 * numSiSamples / total) + "%");
			mTimeText.setText("Hourly Summary: " + PreferenceManager.getDefaultSharedPreferences(this)
					.getString(AudioRecorderService.PERCENTAGE_PREV_KEY, "") + " to "  + PreferenceManager.getDefaultSharedPreferences(this)
					.getString(AudioRecorderService.PERCENTAGE_KEY, ""));
		}
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
					.equals(AudioRecorderService.AUDIORECORDER_ON)) {
				mTvGenericText.setCompoundDrawablesWithIntrinsicBounds(
						R.drawable.mic_on, 0, 0, 0);
			} else if (intent.getAction().equals(
					AudioRecorderService.AUDIORECORDER_OFF)) {
				mTvGenericText.setCompoundDrawablesWithIntrinsicBounds(
						R.drawable.mic_off, 0, 0, 0);
				mTvGenericText.setText(AudioRecorderService.text);
			}
		}
	}

	/**
	 * Listens for when the percentages are renewed daily
	 */
	class SamplePercentageReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(
					AudioRecorderService.CALCULATE_PERCENTAGE))
				calculatePercentages();
		}
	}

	/*-------------------------------HANDLER FUNCTIONALITY-------------------------------*/

	/**
	 * Receives data from setActivityText() in RehearsalAudioRecorder and
	 * displays it to the user
	 */

	Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			mTvGenericText
					.setText(": "
							+ msg.getData()
									.getString(
											AudioRecorderService.AUDIORECORDER_NEWTEXT_CONTENT));
		}
	};

	public static Handler getHandler() {
		return sMessageHandler;
	}
}