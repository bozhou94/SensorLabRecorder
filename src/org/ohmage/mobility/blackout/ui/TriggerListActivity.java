package org.ohmage.mobility.blackout.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import org.ohmage.mobility.blackout.Blackout;
import org.ohmage.mobility.blackout.base.BlackoutList;
import org.ohmage.mobility.blackout.base.TriggerActionDesc;
import org.ohmage.mobility.blackout.base.TriggerBase;
import org.ohmage.mobility.blackout.base.TriggerDB;
import org.ohmage.mobility.blackout.notif.NotifDesc;
import org.ohmage.mobility.blackout.notif.NotifEditActivity;
import org.ohmage.mobility.blackout.notif.Notifier;
import org.ohmage.mobility.blackout.utils.TrigPrefManager;
import org.ohmage.mobility.blackout.utils.TrigTextInput;

import edu.dartmouth.cs.audiorecorder.R;

public class TriggerListActivity extends ListActivity implements
		OnClickListener {

	private static final String PREF_FILE_NAME = TriggerListActivity.class
			.getName();

	public static final String KEY_ACTIONS = TriggerListActivity.class
			.getName() + ".actions";
	// public static final String KEY_ADMIN_MODE =
	// TriggerListActivity.class.getName() + ".admin_mode";
	private static final String KEY_SAVE_DIALOG_TRIG_ID = TriggerListActivity.class
			.getName() + ".dialog_trig_id";
	private static final String KEY_SAVE_SEL_ACTIONS = TriggerListActivity.class
			.getName() + ".selected_actions";
	private static final String KEY_SAVE_DIALOG_TEXT = TriggerListActivity.class
			.getName() + ".dialog_text";

	private static final int MENU_ID_DELETE_TRIGGER = Menu.FIRST;
	private static final int MENU_ID_NOTIF_SETTINGS = Menu.FIRST + 1;
	private static final int MENU_ID_SETTINGS = Menu.FIRST + 2;
	// private static final int MENU_ID_ADMIN_LOGIN = Menu.FIRST + 3;
	// private static final int MENU_ID_ADMIN_LOGOFF = Menu.FIRST + 4;

	private static final int DIALOG_ID_ADD_NEW = 0;
	private static final int DIALOG_ID_PREFERENCES = 1;
	private static final int DIALOG_ID_ACTION_SEL = 2;
	private static final int DIALOG_ID_DELETE = 3;
	private static final int DIALOG_ID_ADMIN_PASS = 4;

	private static final int REQ_EDIT_NOTIF = 0;

	private static final String TAG = "TriggerListActivity";

	private Cursor mCursor;
	private TriggerDB mDb;
	private BlackoutList mTrigMap;
	// private String[] mActions;
	private int mDialogTrigId = -1;
	private String mDialogText = null;
	private boolean[] mActSelected = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.trigger_main_list);

		mTrigMap = new BlackoutList();

		TextView tv = (TextView) findViewById(R.id.add_new_label);
		tv.setText("Blackouts");

		ImageButton bAdd = (ImageButton) findViewById(R.id.button_add_new);
		bAdd.setOnClickListener(this);

		getListView().setHeaderDividersEnabled(true);

		mDb = new TriggerDB(this);
		mDb.open();

		populateTriggerList();
		registerForContextMenu(getListView());

		TrigPrefManager.registerPreferenceFile(this, PREF_FILE_NAME);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		//mCursor.close();
		mDb.close();
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putInt(KEY_SAVE_DIALOG_TRIG_ID, mDialogTrigId);
		outState.putBooleanArray(KEY_SAVE_SEL_ACTIONS, mActSelected);
		outState.putString(KEY_SAVE_DIALOG_TEXT, mDialogText);
	}

	@Override
	protected void onRestoreInstanceState(Bundle state) {
		super.onRestoreInstanceState(state);

		mDialogTrigId = state.getInt(KEY_SAVE_DIALOG_TRIG_ID, -1);
		mActSelected = state.getBooleanArray(KEY_SAVE_SEL_ACTIONS);
		mDialogText = state.getString(KEY_SAVE_DIALOG_TEXT);
	}

	private String getDisplayTitle(/* String trigType, */String trigDesc) {

		TriggerBase trig = new Blackout(); // mTrigMap.getTrigger(trigType);

		// if(trig == null) {
		// return null;
		// }

		return trig.getDisplayTitle(this, trigDesc);
	}

	private int getTrigTypeIcon(/* String trigType */) {

		TriggerBase trig = new Blackout();// mTrigMap.getTrigger(trigType);

		// if(trig == null) {
		// return R.drawable.icon;
		// }

		return trig.getIcon();
	}

	private String getDisplaySummary(/* String trigType, */String trigDesc) {

		TriggerBase trig = new Blackout();// mTrigMap.getTrigger(trigType);

		// if(trig == null) {
		// return null;
		// }

		return trig.getDisplaySummary(this, trigDesc);
	}

	private void toggleTrigger(int trigId, boolean enable) {
		Cursor c = mDb.getTrigger(trigId);

		if (c.moveToFirst()) {
			// String trigType = c.getString(
			// c.getColumnIndexOrThrow(TriggerDB.KEY_TRIG_TYPE));
			
			String trigDesc = c.getString(c
					.getColumnIndexOrThrow(TriggerDB.KEY_TRIG_DESCRIPT));

			TriggerBase trig = new Blackout();// mTrigMap.getTrigger(trigType);

			if (trig != null) {

				if (enable) {
					trig.startTrigger(this, trigId, trigDesc);
				} else {
					trig.stopTrigger(this, trigId, trigDesc);
				}
			}
		}

		c.close();
	}

	private void editTrigger(int trigId, String trigDesc) {

		TriggerBase trig = new Blackout();// mTrigMap.getTrigger(trigType);

		if (trig != null) {
			trig.launchTriggerEditActivity(this, trigId, trigDesc, true);
		}
	}

	private void deleteTrigger(int trigId) {

		TriggerBase trig = new Blackout();// mTrigMap.getTrigger(trigId);

		trig.deleteTrigger(this, trigId);
	}

	private void populateTriggerList() {

		// The viewbinder class to define each list item
		class CategListViewBinder implements SimpleCursorAdapter.ViewBinder {
			@Override
			public boolean setViewValue(View view, Cursor c, int colIndex) {

				String trigDesc = c.getString(c
						.getColumnIndexOrThrow(TriggerDB.KEY_TRIG_DESCRIPT));
				switch (view.getId()) {
				
				case R.id.text1:
					String title = getDisplayTitle(trigDesc);
					((TextView) view).setText(title == null ? "" : title);
					return true;
					
				case R.id.text2:
					String summary = getDisplaySummary(trigDesc);

					((TextView) view).setText(summary == null ? "" : summary);
					return true;

				case R.id.button_actions_edit: // edit surveys button
					int trigId = c.getInt(c
							.getColumnIndexOrThrow(TriggerDB.KEY_ID));

					String actDesc = c
							.getString(c
									.getColumnIndexOrThrow(TriggerDB.KEY_TRIG_ACTIVE_DESCRIPT));

					Button bAct = (Button) view;
					// bAct.setTag(trigId);
					bAct.setFocusable(false);
					bAct.setText(TriggerListActivity.this.mDb
							.getActionDescription(trigId) ? "on" : "off");
					TriggerActionDesc desc = new TriggerActionDesc();
					desc.loadBoolean(actDesc);
					// bAct.setText("On");

					bAct.setTag(new Integer(trigId));
					
					view.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							mDialogTrigId = (Integer) v.getTag();
							mActSelected = null;
							// removeDialog(DIALOG_ID_ACTION_SEL);
							// showDialog(DIALOG_ID_ACTION_SEL);

							// toggle onness of the trigger

							// int trigId = mDialogTrigId;
							if (TriggerListActivity.this.mDb
									.updateActionDescription(
											mDialogTrigId,
											!TriggerListActivity.this.mDb
													.getActionDescription(mDialogTrigId))) {
								((Button) v).setText(TriggerListActivity.this.mDb
										.getActionDescription(mDialogTrigId) ? "on"
										: "off");

								Notifier.refreshNotification(
										TriggerListActivity.this, true);
								
								toggleTrigger(
										mDialogTrigId,
										TriggerListActivity.this.mDb
												.getActionDescription(mDialogTrigId));
							}// else
								//Log.w(TAG, "Failed to update.");

						}
					});
					return true;
				
				case R.id.icon_trigger_type:
					ImageView iv = (ImageView) view;
					iv.setImageResource(getTrigTypeIcon());
					return true;
				}

				return false;
			}
		}
		
		mCursor = mDb.getAllTriggers();

		mCursor.moveToFirst();
		startManagingCursor(mCursor);

		String[] from = new String[] { TriggerDB.KEY_ID, TriggerDB.KEY_ID,
				TriggerDB.KEY_ID, TriggerDB.KEY_ID };

		int[] to = new int[] { R.id.text1, R.id.text2,
				R.id.button_actions_edit, R.id.icon_trigger_type };
	
		SimpleCursorAdapter triggers = new SimpleCursorAdapter(this,
				R.layout.trigger_main_list_row, mCursor, from, to);
	
		triggers.setViewBinder(new CategListViewBinder());
		setListAdapter(triggers);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		
		//Log.v(TAG, "Click on a blackout");
		if (!mCursor.moveToPosition(position)) {
			// TODO this should not happen. log
			return;
		}

		String trigDesc = mCursor.getString(mCursor
				.getColumnIndexOrThrow(TriggerDB.KEY_TRIG_DESCRIPT));
		int trigId = mCursor.getInt(mCursor
				.getColumnIndexOrThrow(TriggerDB.KEY_ID));

		// String trigType = mCursor.getString(
		// mCursor.getColumnIndexOrThrow(TriggerDB.KEY_TRIG_TYPE));

		editTrigger(trigId, trigDesc);
		
	}

	private Dialog createDeleteConfirmDialog(int trigId) {

		return new AlertDialog.Builder(this)
				.setNegativeButton("No", null)
				.setTitle("Confirm delete")
				.setMessage("Delete trigger?")
				.setPositiveButton("Yes",
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								deleteTrigger(mDialogTrigId);
								mCursor.requery();
							}
						}).create();
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_ID_ADD_NEW:
			new Blackout().launchTriggerCreateActivity(
					TriggerListActivity.this, true);
			// Intent intent = new Intent(this, BlackoutEditActivity.class);
			// this.startActivity(intent);
			return null;
			// return createAddNewSelDialog();

			// case DIALOG_ID_PREFERENCES:
			// return createEditPrefSelDialog();

		case DIALOG_ID_ACTION_SEL:

			// return createEditActionDialog(mDialogTrigId);

		case DIALOG_ID_DELETE:
			return createDeleteConfirmDialog(mDialogTrigId);

			// case DIALOG_ID_ADMIN_PASS:
			// return createAdminPassDialog();
		}

		return null;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean ret = super.onPrepareOptionsMenu(menu);

		return ret;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {

		case MENU_ID_NOTIF_SETTINGS:

			Intent i = new Intent(this, NotifEditActivity.class);
			i.putExtra(NotifEditActivity.KEY_NOTIF_CONFIG,
					NotifDesc.getGlobalDesc(this));
			startActivityForResult(i, REQ_EDIT_NOTIF);
			return true;

		case MENU_ID_SETTINGS:

			showDialog(DIALOG_ID_PREFERENCES);
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		if (requestCode == REQ_EDIT_NOTIF && data != null) {
			String desc = data
					.getStringExtra(NotifEditActivity.KEY_NOTIF_CONFIG);

		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, MENU_ID_DELETE_TRIGGER, 0, "Delete").setEnabled(true/*
																		 * isAdminLoggedIn
																		 * () ||
																		 * TrigUserConfig
																		 * .
																		 * removeTrigers
																		 */);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {

		int pos = ((AdapterContextMenuInfo) item.getMenuInfo()).position;
		mCursor.moveToPosition(pos);

		int trigId = mCursor.getInt(mCursor
				.getColumnIndexOrThrow(TriggerDB.KEY_ID));

		switch (item.getItemId()) {

		case MENU_ID_DELETE_TRIGGER:
			mDialogTrigId = trigId;
			showDialog(DIALOG_ID_DELETE);
			return true;

		default:
			break;
		}

		return super.onContextItemSelected(item);
	}

	@Override
	public void onClick(View v) {

		if (v.getId() == R.id.button_add_new) {

			showDialog(DIALOG_ID_ADD_NEW);
		}
	}

}
