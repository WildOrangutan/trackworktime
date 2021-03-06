/*
 * This file is part of TrackWorkTime (TWT).
 *
 * TWT is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TWT is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with TWT. If not, see <http://www.gnu.org/licenses/>.
 */
package org.zephyrsoft.trackworktime;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.viewpager2.widget.ViewPager2;

import org.pmw.tinylog.Logger;
import org.zephyrsoft.trackworktime.database.DAO;
import org.zephyrsoft.trackworktime.model.Event;
import org.zephyrsoft.trackworktime.model.Task;
import org.zephyrsoft.trackworktime.model.TypeEnum;
import org.zephyrsoft.trackworktime.model.Week;
import org.zephyrsoft.trackworktime.model.WeekPlaceholder;
import org.zephyrsoft.trackworktime.options.Key;
import org.zephyrsoft.trackworktime.timer.TimeCalculator;
import org.zephyrsoft.trackworktime.timer.TimerManager;
import org.zephyrsoft.trackworktime.util.BackupUtil;
import org.zephyrsoft.trackworktime.util.DateTimeUtil;
import org.zephyrsoft.trackworktime.util.ExternalNotificationManager;
import org.zephyrsoft.trackworktime.util.PreferencesUtil;
import org.zephyrsoft.trackworktime.weektimes.WeekAdapter;
import org.zephyrsoft.trackworktime.weektimes.WeekIndexConverter;
import org.zephyrsoft.trackworktime.weektimes.WeekStateCalculatorFactory;
import org.zephyrsoft.trackworktime.weektimes.WeekStateLoaderFactory;
import org.zephyrsoft.trackworktime.weektimes.WeekStateLoaderManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.TimeZone;

import static java.lang.Math.abs;

/**
 * Main activity of the application.
 *
 * @author Mathis Dirksen-Thedens
 */
public class WorkTimeTrackerActivity extends AppCompatActivity {
	private static final int PERMISSION_REQUEST_CODE_BACKUP = 1;
	private static final int PERMISSION_REQUEST_CODE_RESTORE = 2;

	private static final String KEY_CURRENT_WEEK = "current_week";

	private enum MenuAction {
		EDIT_EVENTS, EDIT_TASKS, INSERT_DEFAULT_TIMES, OPTIONS, REQUEST_TO_IGNORE_BATTERY_OPTIMIZATIONS,
		USE_CURRENT_LOCATION, REPORTS, BACKUP, RESTORE, ABOUT, SEND_LOGS, RAISE_EXCEPTION, RECENTER_WEEK;

		public static MenuAction byOrdinal(int ordinal) {
			return values()[ordinal];
		}
	}

	private Spinner task = null;
	private EditText text = null;
	private Button clockInButton = null;
	private Button clockOutButton = null;
	private ViewPager2 weekPager = null;
	private MenuItem recenterMenuItem;

	private static WorkTimeTrackerActivity instance = null;

	private boolean visible = false;

	private SharedPreferences preferences;
	private DAO dao = null;
	private TimerManager timerManager = null;
	private ExternalNotificationManager externalNotificationManager = null;
	private ArrayAdapter<Task> tasksAdapter;
	private boolean reloadTasksOnResume = false;
	private List<Task> tasks;
	private WeekIndexConverter weekIndexConverter;
	private WeekAdapter weekAdapter;

	private void checkAllOptions() {
		int disabledSections = PreferencesUtil.checkAllPreferenceSections();

		if (disabledSections > 0) {
			// show message to user
			Intent messageIntent = Basics
				.getInstance()
				.createMessageIntent(
					disabledSections == 1
						? "One option was disabled due to invalid values or value combinations.\n\nYou can re-enable it after you checked the values you entered."
						: String.valueOf(disabledSections)
							+ " options were disabled due to invalid values or value combinations.\n\nYou can re-enable them after you checked the values you entered.",
					null);
			startActivity(messageIntent);
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		instance = this;
		Basics basics = Basics.getOrCreateInstance(getApplicationContext());
		// fill basic data from central structures
		preferences = basics.getPreferences();
		dao = basics.getDao();
		weekIndexConverter = new WeekIndexConverter(dao, DateTimeUtil.getCurrentTimeZone());
		timerManager = basics.getTimerManager();
		externalNotificationManager = basics.getExternalNotificationManager();

		setContentView(R.layout.main);

		findAllViewsById();

		initWeekPager(savedInstanceState);

		clockInButton.setOnClickListener(v -> clockInAction(0));
		clockInButton.setOnLongClickListener(v -> {
            Intent i = new Intent(getApplicationContext(), TimeAheadActivity.class);
            String typeString = null;
            if (timerManager.isTracking()) {
                typeString = getString(R.string.clockInChange);
            } else {
                typeString = getString(R.string.clockIn);
            }
            i.putExtra(Constants.TYPE_EXTRA_KEY, 0);
            i.putExtra(Constants.TYPE_STRING_EXTRA_KEY, typeString);
            startActivity(i);
            return true;
        });

		clockOutButton.setOnClickListener(v -> clockOutAction(0));
		clockOutButton.setOnLongClickListener(v -> {
            Intent i = new Intent(getApplicationContext(), TimeAheadActivity.class);
            String typeString = null;
            if (timerManager.isTracking()) {
                typeString = getString(R.string.clockOut);
                i.putExtra(Constants.TYPE_EXTRA_KEY, 1);
                i.putExtra(Constants.TYPE_STRING_EXTRA_KEY, typeString);
                startActivity(i);
            }
            return true;
        });


		// delegate the rest of the work to onResume()
		reloadTasksOnResume = true;

		// check options for logical errors
		checkAllOptions();

		if (!preferences.getBoolean(getString(R.string.keyBackupSettingAsked), false)) {
			DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
                final Editor editor = preferences.edit();
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        editor.putBoolean(getText(R.string.keyBackupEnabled) + "", true);
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        editor.putBoolean(getText(R.string.keyBackupEnabled) + "", false);
                        break;
                }
                editor.putBoolean(getString(R.string.keyBackupSettingAsked), true);
                editor.commit();
            };
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.backup_on_google_servers)
				.setPositiveButton(R.string.yes, dialogClickListener)
				.setNegativeButton(R.string.no, dialogClickListener).show();
		}
	}

	private void initWeekPager(@Nullable Bundle state) {
		weekPager.setOffscreenPageLimit(1);
		initWeekPagerAdapter();
		initWeekPagerAnimation();
		initWeekPagerPosition(state);
		initWeekPagerChangeCallback();
	}

	private void initWeekPagerAdapter() {
		WeekStateLoaderManager weekStateLoaderManager = createWeekLoaderManger();
		OnClickListener weekClickListener = v -> showCurrentEventList();
		weekAdapter = new WeekAdapter(weekIndexConverter, weekStateLoaderManager, weekClickListener);
		weekPager.setAdapter(weekAdapter);
	}

	private WeekStateLoaderManager createWeekLoaderManger() {
		WeekStateLoaderFactory weekStateLoaderFactory = createWeekLoaderFactory();
		return new WeekStateLoaderManager(weekStateLoaderFactory);
	}

	private WeekStateLoaderFactory createWeekLoaderFactory() {
		TimeCalculator timeCalculator = Basics.getInstance().getTimeCalculator();
		WeekStateCalculatorFactory weekStateCalculatorFactory = new WeekStateCalculatorFactory(
				this, dao, timerManager, timeCalculator, preferences);
		return new WeekStateLoaderFactory(weekStateCalculatorFactory);
	}

	private void initWeekPagerAnimation() {
		weekPager.setPageTransformer((view, position) -> {
			if(position >= 1 || position < -1) {
				return;
			}
			if (position >= 0) {
				view.setAlpha(1);
				view.setTranslationX(0);
				view.setScaleX(1);
				view.setScaleY(1);
			} else if (position >= -1) {
				view.setAlpha(1 + position);
				view.setPivotY(0.5f * view.getHeight());
				view.setTranslationX(view.getWidth() * -position);
				final float MIN_SCALE = 0.95f;
				float scaleFactor = MIN_SCALE + (1 - MIN_SCALE) * (1 - Math.abs(position));
				view.setScaleX(scaleFactor);
				view.setScaleY(scaleFactor);
			}
		});
	}

	private void initWeekPagerPosition(@Nullable Bundle state) {
		Week restoredWeek = state == null ? null : state.getParcelable(KEY_CURRENT_WEEK);
		Week initialWeek = restoredWeek == null ? getTodaysWeek() : restoredWeek;
		showWeek(initialWeek, false);
	}

	private void recenterWeek(boolean animate) {
		Week todaysWeek = getTodaysWeek();
		showWeek(todaysWeek, animate);
	}

	private Week getTodaysWeek() {
		final String todaysWeekStart = DateTimeUtil.getWeekStartAsString(DateTimeUtil.getCurrentDateTime());
		Week todaysWeek = dao.getWeek(todaysWeekStart);
		return todaysWeek != null
				? todaysWeek
				: new WeekPlaceholder(todaysWeekStart);
	}

	private void showWeek(@NonNull Week week, boolean animate) {
		int weekIndex = weekIndexConverter.getIndexForWeek(week);
		weekPager.setCurrentItem(weekIndex, animate);
	}

	private void initWeekPagerChangeCallback() {
		weekPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int selectedWeekIndex) {
				super.onPageSelected(selectedWeekIndex);
				refreshRecenterMenuItem();
			}
		});
	}

	@Override public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
		super.onSaveInstanceState(outState, outPersistentState);
		outState.putParcelable(KEY_CURRENT_WEEK, getCurrentlyDisplayedWeek());
	}

	@Override
	protected void onStart() {
		super.onStart();

		// request location permission if it was removed in the meantime
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (prefs.getBoolean(Key.LOCATION_BASED_TRACKING_ENABLED.getName(), false)
				|| prefs.getBoolean(Key.WIFI_BASED_TRACKING_ENABLED.getName(), false)
				) {
			if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(this,
						new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1000);
			}
		}
	}

	/**
	 * @param minutesToPredate
	 *            if greater than 0, predate the event this many minutes
	 */
	public void clockInAction(int minutesToPredate) {
		if (minutesToPredate < 0) {
			throw new IllegalArgumentException("no negative argument allowed");
		}
		// commit text field
		text.clearFocus();

		Task selectedTask = (Task) task.getSelectedItem();
		String description = text.getText().toString();
		timerManager.startTracking(minutesToPredate, selectedTask, description);
		externalNotificationManager.notifyPebble("started tracking");
		refreshView();
	}

	/**
	 * @param minutesToPredate
	 *            if greater than 0, predate the event this many minutes
	 */
	public void clockOutAction(int minutesToPredate) {
		if (minutesToPredate < 0) {
			throw new IllegalArgumentException("no negative argument allowed");
		}

		timerManager.stopTracking(minutesToPredate);
		externalNotificationManager.notifyPebble("stopped tracking");
		refreshView();
	}


	/**
	 * Reloads the view's data if the view is currently shown.
	 */
	public static void refreshViewIfShown() {
		if (instance != null && instance.visible) {
			Logger.debug("refreshing main view (it is visible at the moment)");
			instance.refreshView();
		}
	}

	protected void refreshView() {
		clockOutButton.setEnabled(timerManager.isTracking());
		Task taskToSelect = null;
		if (timerManager.isTracking()) {
			clockInButton.setText(R.string.clockInChange);
			taskToSelect = timerManager.getCurrentTask();
		} else {
			clockInButton.setText(R.string.clockIn);
			taskToSelect = dao.getDefaultTask();
		}
		if (taskToSelect != null) {
			int i = 0;
			for (Task oneTask : tasks) {
				if (oneTask.getId().equals(taskToSelect.getId())) {
					task.setSelection(i);
					break;
				}
				i++;
			}
		}
		weekAdapter.notifyDataSetChanged();
		refreshRecenterMenuItem();
	}

	private void refreshRecenterMenuItem() {
		if(recenterMenuItem == null) {
			// Can happen during startup, since onCreateOptionsMenu() is called after onResume()
			return;
		}

		int difference = getDisplayedToTodaysWeekDifference();

		boolean itemVisible = abs(difference) > 0;
		recenterMenuItem.setVisible(itemVisible);

		if(!itemVisible) {
			return;
		}
		@DrawableRes int icon = difference > 0
				? R.drawable.ic_calendar_recenter_right
				: R.drawable.ic_calendar_recenter_left;
		recenterMenuItem.setIcon(icon);
	}

	/**
	 * @return 0, when current week is displayed, negative value, when currently displayed week is
	 * in the past, and positive value, when displaying future week */
	private int getDisplayedToTodaysWeekDifference() {
		Week todaysWeek = getTodaysWeek();
		int todaysIndex = weekIndexConverter.getIndexForWeek(todaysWeek);
		int shownIndex = getCurrentlyDisplayedWeekIndex();
		return todaysIndex - shownIndex;
	}

	private void setupTasksAdapter() {
		tasks = dao.getActiveTasks();
		tasksAdapter = new ArrayAdapter<>(this, R.layout.list_item_spinner, tasks);
		tasksAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		task.setAdapter(tasksAdapter);
	}

	private void findAllViewsById() {
		task = findViewById(R.id.task);
		text = findViewById(R.id.text);
		clockInButton = findViewById(R.id.clockInButton);
		clockOutButton = findViewById(R.id.clockOutButton);
		weekPager = findViewById(R.id.week_pager);
	}

	/**
	 * Mark task list as changed so it will be re-read from the database the next time the GUI is refreshed.
	 */
	public void refreshTasks() {
		reloadTasksOnResume = true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, MenuAction.EDIT_EVENTS.ordinal(), MenuAction.EDIT_EVENTS.ordinal(), R.string.edit_events)
			.setIcon(R.drawable.ic_menu_edit);
		menu.add(Menu.NONE, MenuAction.EDIT_TASKS.ordinal(), MenuAction.EDIT_TASKS.ordinal(), R.string.edit_tasks)
			.setIcon(R.drawable.ic_menu_sort_by_size);
		menu.add(Menu.NONE, MenuAction.INSERT_DEFAULT_TIMES.ordinal(), MenuAction.INSERT_DEFAULT_TIMES.ordinal(), R.string.insert_default_times)
			.setIcon(R.drawable.ic_menu_mark);
		menu.add(Menu.NONE, MenuAction.OPTIONS.ordinal(), MenuAction.OPTIONS.ordinal(), R.string.options)
			.setIcon(R.drawable.ic_menu_preferences);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			menu.add(Menu.NONE, MenuAction.REQUEST_TO_IGNORE_BATTERY_OPTIMIZATIONS.ordinal(), MenuAction.REQUEST_TO_IGNORE_BATTERY_OPTIMIZATIONS.ordinal(), R.string.request_to_ignore_battery_optimizations)
				.setIcon(R.drawable.ic_menu_preferences);
		}
		menu.add(Menu.NONE, MenuAction.USE_CURRENT_LOCATION.ordinal(), MenuAction.USE_CURRENT_LOCATION.ordinal(), R.string.use_current_location)
			.setIcon(R.drawable.ic_menu_compass);
		menu.add(Menu.NONE, MenuAction.REPORTS.ordinal(), MenuAction.REPORTS.ordinal(), R.string.reports)
			.setIcon(R.drawable.ic_menu_agenda);
		menu.add(Menu.NONE, MenuAction.BACKUP.ordinal(), MenuAction.BACKUP.ordinal(), R.string.backup);
		menu.add(Menu.NONE, MenuAction.RESTORE.ordinal(), MenuAction.RESTORE.ordinal(), R.string.restore);
		menu.add(Menu.NONE, MenuAction.ABOUT.ordinal(), MenuAction.ABOUT.ordinal(), R.string.about)
			.setIcon(R.drawable.ic_menu_star);
		menu.add(Menu.NONE, MenuAction.SEND_LOGS.ordinal(), MenuAction.SEND_LOGS.ordinal(), R.string.sendLogs);
		if (Basics.getOrCreateInstance(this).isDevelopmentVersion()) {
			menu.add(Menu.NONE, MenuAction.RAISE_EXCEPTION.ordinal(), MenuAction.RAISE_EXCEPTION.ordinal(), "[DEV] Raise Exception")
				.setIcon(R.drawable.ic_menu_star);
		}

		int recenterId = MenuAction.RECENTER_WEEK.ordinal();
		recenterMenuItem = menu.add(Menu.NONE, recenterId, recenterId, R.string.recenter_week);
		recenterMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		recenterMenuItem.setVisible(false);
		refreshRecenterMenuItem();

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (MenuAction.byOrdinal(item.getItemId())) {
			case RECENTER_WEEK:
				recenterWeek(true);
				return true;
			case EDIT_EVENTS:
				showCurrentEventList();
				return true;
			case EDIT_TASKS:
				showTaskList();
				return true;
			case INSERT_DEFAULT_TIMES:
				showInsertDefaultTimes();
				return true;
			case OPTIONS:
				showOptions();
				return true;
			case REQUEST_TO_IGNORE_BATTERY_OPTIMIZATIONS:
				showRequestToIgnoreBatteryOptimizations();
				return true;
			case USE_CURRENT_LOCATION:
				useCurrentLocationAsWorkplace();
				return true;
			case REPORTS:
				showReports();
				return true;
			case BACKUP:
				backupToSd();
				return true;
			case RESTORE:
				restoreFromSd();
				return true;
			case ABOUT:
				showAbout();
				return true;
			case SEND_LOGS:
				sendLogs();
				return true;
			case RAISE_EXCEPTION:
				throw new IllegalStateException("this exception is for testing purposes only");
			default:
				throw new IllegalArgumentException("options menu: unknown item selected");
		}
	}

	private void showCurrentEventList() {
		Week currentWeek = getCurrentlyDisplayedWeek();
		showEventList(currentWeek);
	}

	private @NonNull Week getCurrentlyDisplayedWeek() {
		int weekIndex = getCurrentlyDisplayedWeekIndex();
		return weekIndexConverter.getWeekForIndex(weekIndex);
	}

	private int getCurrentlyDisplayedWeekIndex() {
		return weekPager.getCurrentItem();
	}

	private void showEventList(Week week) {
		Logger.debug("showing EventList");
		Intent i = new Intent(this, EventListActivity.class);
		if(week == null) {
			Logger.error("Trying to show event list for null week");
			return;
		}
		i.putExtra(Constants.WEEK_START_EXTRA_KEY, week.getStart());
		startActivity(i);
	}

	private void showTaskList() {
		Logger.debug("showing TaskList");
		Intent i = new Intent(this, TaskListActivity.class);
		startActivity(i);
	}

	private void showInsertDefaultTimes() {
		Logger.debug("showing InsertDefaultTimes");
		Intent i = new Intent(this, InsertDefaultTimesActivity.class);
		startActivity(i);
	}

	private void showOptions() {
		Logger.debug("showing Options");
		Intent i = new Intent(this, OptionsActivity.class);
		startActivity(i);
	}

	private void showRequestToIgnoreBatteryOptimizations() {
		Logger.debug("showing request to ignore battery optimizations");
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle(getString(R.string.request_to_ignore_battery_optimizations_title));
		alert.setMessage(getString(R.string.request_to_ignore_battery_optimizations_text));
		alert.setPositiveButton(getString(R.string.request_to_ignore_battery_optimizations_go_to_settings),
			(dialog, whichButton) -> Basics.getInstance().openBatterySettings());
		alert.setNegativeButton(getString(R.string.cancel), (dialog, whichButton) -> {
			// do nothing
		});
		alert.show();
	}

	private void useCurrentLocationAsWorkplace() {
		Logger.debug("use current location as work place");
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle(getString(R.string.use_current_location));
		alert.setMessage(getString(R.string.really_use_current_location));
		alert.setPositiveButton(getString(R.string.ok), (dialog, whichButton) -> Basics.getInstance().useCurrentLocationAsWorkplace(WorkTimeTrackerActivity.this));
		alert.setNegativeButton(getString(R.string.cancel), (dialog, whichButton) -> {
            // do nothing
        });
		alert.show();
	}

	private void showReports() {
		Logger.debug("showing Reports");
		Intent i = new Intent(this, ReportsActivity.class);
		startActivity(i);
	}

	private void showAbout() {
		Logger.debug("showing About");
		Intent i = new Intent(this, AboutActivity.class);
		startActivity(i);
	}

	private void sendLogs() {
		Logger.debug("sending logs");

		new AlertDialog.Builder(this)
			.setTitle(R.string.sendLogs)
			.setMessage(R.string.sendLogsQuestion)
			.setPositiveButton(R.string.ok, (DialogInterface dialog, int id) -> doSendLogs())
			.setNegativeButton(R.string.cancel, (DialogInterface dialog, int id) -> {
				// do nothing
			})
			.create()
			.show();
	}

	private void doSendLogs() {
		Intent emailIntent = new Intent(Intent.ACTION_SEND);
		emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.sendLogsSubject));
		Uri fileUri = FileProvider.getUriForFile(this,
			BuildConfig.APPLICATION_ID + ".util.GenericFileProvider", Basics.getInstance().getCurrentLogFile());
		emailIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
		emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		emailIntent.setType("text/plain");
		String to[] = {getString(R.string.email)};
		emailIntent.putExtra(Intent.EXTRA_EMAIL, to);
		startActivity(Intent.createChooser(emailIntent , getString(R.string.sendLogs)));
	}

	@Override
	protected void onResume() {
		Logger.debug("onResume called");
		visible = true;
		if (reloadTasksOnResume) {
			reloadTasksOnResume = false;
			setupTasksAdapter();
		}

		Basics.getInstance().safeCheckPersistentNotification();

		refreshView();
		super.onResume();
	}

	@Override
	protected void onPause() {
		Logger.debug("onPause called");
		dao.close();
		visible = false;
		super.onPause();
	}

	/**
	 * Get the instance of this activity. If it was garbage-collected in the meantime, throw an exception.
	 */
	public static WorkTimeTrackerActivity getInstance() {
		if (instance == null) {
			throw new IllegalStateException("the main activity is not created yet or was dumped in the meantime");
		}
		return instance;
	}

	/**
	 * Get the instance of this activity. If it was garbage-collected in the meantime, return {@code null}.
	 */
	public static WorkTimeTrackerActivity getInstanceOrNull() {
		return instance;
	}

	private Event getLastEventIfClockIn() {
		Event event = dao.getLastEventBefore(DateTimeUtil.getCurrentDateTime());
		if (event != null && event.getType() != null && event.getType().equals(TypeEnum.CLOCK_IN.getValue())) {
			return event;
		} else {
			return null;
		}
	}

	private static boolean equalsWithNullEqualsEmptyString(String one, String two) {
		return (one == null && two == null) || (one != null && one.length() == 0 && two == null)
			|| (one != null && one.length() == 0 && two == null) || (one == null && two != null && two.length() == 0)
			|| (one != null && one.equals(two));
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions,
			int[] grantResults) {
		switch (requestCode) {
			case PERMISSION_REQUEST_CODE_BACKUP:
				if (grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					backupToSd();
				}
				break;
			case PERMISSION_REQUEST_CODE_RESTORE:
				if (grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					restoreFromSd();
				}
				break;
			default:
				super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Backup, Restore
	// ---------------------------------------------------------------------------------------------
	private static final String BACKUP_FILE = "backup.csv";

	/**
	 * Check if file exists and ask user if so.
	 */
	private void backupToSd() {
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this,
					new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE_BACKUP);
			return;
		}
		final File backupDir = new File(Environment.getExternalStorageDirectory(), Constants.DATA_DIR);
		final File backupFile = new File(backupDir, BACKUP_FILE);
		if (backupDir == null) {
			Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_LONG).show();
			return;
		}
		if (backupFile.exists()) {
			final AlertDialog.Builder builder = new AlertDialog.Builder(this);
			final String msgBackupOverwrite = String.format(
				getString(R.string.backup_overwrite), backupFile);
			builder.setMessage(msgBackupOverwrite)
				.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    backup(backupFile);
                    dialog.dismiss();
                })
				.setNegativeButton(android.R.string.cancel, null).show();
		} else {
			backup(backupFile);
		}
	}

	private void backup(final File backupFile) {
		// do in background
		new AsyncTask<Void, Void, Boolean>() {
			private ProgressDialog dialog;

			@Override
			protected void onPreExecute() {
				dialog = ProgressDialog.show(WorkTimeTrackerActivity.this,
					getString(R.string.backup), getString(R.string.please_wait), true);
				dialog.show();
			}

			@Override
			protected Boolean doInBackground(Void... none) {
				return BackupUtil.doBackup(getApplicationContext(), backupFile);
			}

			@Override
			protected void onPostExecute(Boolean successful) {
				if (successful) {
					refreshView();
					Toast.makeText(WorkTimeTrackerActivity.this, backupFile.toString(), Toast.LENGTH_LONG).show();
				} else {
					Toast.makeText(WorkTimeTrackerActivity.this, R.string.backup_failed, Toast.LENGTH_LONG).show();
				}
				dialog.dismiss();
			}

		}.execute(null, null);
	}

	private void restoreFromSd() {
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this,
					new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE_RESTORE);
			return;
		}
		final File backupDir = new File(Environment.getExternalStorageDirectory(), Constants.DATA_DIR);
		final File backupFile = new File(backupDir, BACKUP_FILE);
		if (backupDir == null) {
			final String msgBackupOverwrite = String.format(
				getString(R.string.restore_failed_file_not_found),
				backupFile);
			Toast.makeText(this, msgBackupOverwrite, Toast.LENGTH_LONG).show();
			return;
		}
		if (backupFile.exists()) {
			final Cursor cur = dao.getAllEventsAndTasks();
			final int medCount = cur.getCount();
			cur.close();
			if (medCount > 0) {
				final AlertDialog.Builder builder = new AlertDialog.Builder(this);
				final String msgBackupOverwrite = String.format(
					getString(R.string.restore_warning), backupFile);
				builder.setMessage(msgBackupOverwrite)
					.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        restore(backupFile);
                        dialog.dismiss();
                    })
					.setNegativeButton(android.R.string.cancel, null).show();
			} else {
				// no entries, skip warning
				restore(backupFile);
			}
		} else {
			final String msgBackupOverwrite = String.format(
				getString(R.string.restore_failed_file_not_found),
				backupFile);
			Toast.makeText(this, msgBackupOverwrite, Toast.LENGTH_LONG).show();
		}
	}

	private void restore(final File backupFile) {
		// do in background
		new AsyncTask<Void, Void, Boolean>() {
			private ProgressDialog dialog;

			@Override
			protected void onPreExecute() {
				dialog = ProgressDialog.show(WorkTimeTrackerActivity.this,
					getString(R.string.restore), getString(R.string.please_wait), true);
				dialog.show();
			}

			@Override
			protected Boolean doInBackground(Void... none) {
				try {
					final BufferedReader input = new BufferedReader(
						new InputStreamReader(new FileInputStream(backupFile)));
					dao.restoreFromReader(input);
					return true;
				} catch (IOException e) {
					e.printStackTrace();
					return false;
				}
			}

			@Override
			protected void onPostExecute(Boolean successful) {
				if (successful) {
					refreshView();
					Toast.makeText(WorkTimeTrackerActivity.this, backupFile.toString(), Toast.LENGTH_LONG).show();
				} else {
					Toast.makeText(WorkTimeTrackerActivity.this, R.string.restore_failed, Toast.LENGTH_LONG).show();
				}
				dialog.dismiss();
			}

		}.execute(null, null);
	}
}
