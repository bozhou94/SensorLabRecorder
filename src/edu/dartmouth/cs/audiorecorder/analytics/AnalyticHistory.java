package edu.dartmouth.cs.audiorecorder.analytics;

import edu.dartmouth.cs.audiorecorder.AudioRecorderService;
import edu.dartmouth.cs.audiorecorder.R;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class AnalyticHistory extends Activity {
	
	// Used for status writing
	private static Handler sMessageHandler;
	private ArrayAdapter<String> mAdapter;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.analytic_history);
		ListView myListView = (ListView) findViewById(R.id.list);
		mAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1,
				AudioRecorderService.changeHistory);
		myListView.setAdapter(mAdapter);
	}
	
	@Override
	public void onStart() {
		super.onStart();
		sMessageHandler = mHandler;
		mAdapter.notifyDataSetChanged();
	}
	
	@Override
	public void onStop() {
		super.onStop();
		sMessageHandler = null;
	}
	
	/*-------------------------------HANDLER FUNCTIONALITY-------------------------------*/

	/**
	 * If nothing was added to the list in the previous minute, then the current
	 * status is added, with the list storing a maximum of 10 statuses
	 */
	Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			mAdapter.notifyDataSetChanged();
		}
	};

	public static Handler getHandler() {
		return sMessageHandler;
	}
}
