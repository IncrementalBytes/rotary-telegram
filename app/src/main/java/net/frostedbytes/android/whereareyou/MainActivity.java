package net.frostedbytes.android.whereareyou;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import java.util.Timer;
import java.util.TimerTask;
import net.frostedbytes.android.whereareyou.fragments.FriendListFragment;
import net.frostedbytes.android.whereareyou.fragments.ManageUserFragment;
import net.frostedbytes.android.whereareyou.fragments.MappingFragment;
import net.frostedbytes.android.whereareyou.fragments.UserPreferencesFragment;
import net.frostedbytes.android.whereareyou.models.User;
import net.frostedbytes.android.whereareyou.models.UserLocation;
import net.frostedbytes.android.whereareyou.utils.DateUtils;
import net.frostedbytes.android.whereareyou.utils.LogUtils;

public class MainActivity extends BaseActivity implements FriendListFragment.OnFriendListListener, UserPreferencesFragment.OnPreferencesListener {

  private static final String TAG = MainActivity.class.getSimpleName();

  private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;

  private ActionBar mActionBar;
  private FragmentManager mFragmentManager;
  private FloatingActionButton mSharingActionButton;
  private TextView mStatusHeaderTextView;
  private TextView mStatusTextView;
  private TextView mTimeStampHeaderTextView;
  private TextView mTimeStampTextView;
  private MenuItem mSettingsMenuItem;
  private MenuItem mManageUserMenuItem;

  private Query mUserQuery;
  private ValueEventListener mUserValueListener;

  private User mUser;

  private FusedLocationProviderClient mFusedLocationClient;
  private Location mLastLocation;

  private final Handler mHandler = new Handler();
  private Timer mTimer;
  private TimerTask mTimerTask;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    LogUtils.debug(TAG, "++onCreate(Bundle)");
    setContentView(R.layout.activity_main);

    showProgressDialog(getString(R.string.status_initializing));

    // grab control references
    Toolbar toolbar = findViewById(R.id.main_toolbar);
    mSharingActionButton = findViewById(R.id.main_button_share_location);
    mStatusHeaderTextView = findViewById(R.id.main_text_status_header);
    mStatusTextView = findViewById(R.id.main_text_status);
    mTimeStampHeaderTextView = findViewById(R.id.main_text_timestamp_header);
    mTimeStampTextView = findViewById(R.id.main_text_timestamp);

    // setup tool bar
    setSupportActionBar(toolbar);
    mActionBar = getSupportActionBar();

    // get parameters from previous activity
    String userId = getIntent().getStringExtra(BaseActivity.ARG_USER_ID);
    String userName = getIntent().getStringExtra(BaseActivity.ARG_USER_NAME);

    // look for user in database
    mUserQuery = FirebaseDatabase.getInstance().getReference().child(User.USERS_ROOT).child(userId);
    mUserValueListener = new ValueEventListener() {

      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {

        mUser = dataSnapshot.getValue(User.class);
        if (mUser != null) {
          // found user, update our copy
          mUser.UserId = dataSnapshot.getKey();
        } else {
          // user not found, create a new one in the database
          mUser = new User();
          mUser.UserId = userId;
          mUser.FullName = userName;
          mUserQuery.getRef().setValue(mUser);
        }

        updateUser();
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {

        LogUtils.debug(TAG, "++onCancelled(DatabaseError)");
        LogUtils.error(TAG, databaseError.getMessage());
      }
    };
    mUserQuery.addValueEventListener(mUserValueListener);

    mSharingActionButton.setOnClickListener(view -> {

      if (mUser != null) {
        if (mUser.IsSharing) {
          // turn off location sharing
          mUser.IsSharing = false;
          SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
          sharedPreferences.edit().putBoolean(UserPreferencesFragment.KEY_GET_SHARING_SETTING, mUser.IsSharing).apply();
          FirebaseDatabase.getInstance().getReference().child(User.USERS_ROOT).child(userId).child("IsSharing").setValue(mUser.IsSharing);
          if (mTimer != null) {
            mTimer.cancel();
            mTimer.purge();
            mTimer = null;
          }
        } else {
          // turn on location sharing, after checking permissions
          if (!checkPermissions()) {
            requestPermissions();
          } else {
            mUser.IsSharing = true;
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            sharedPreferences.edit().putBoolean(UserPreferencesFragment.KEY_GET_SHARING_SETTING, mUser.IsSharing).apply();
            FirebaseDatabase.getInstance().getReference().child(User.USERS_ROOT).child(userId).child("IsSharing").setValue(mUser.IsSharing);
            startLocationTask();
          }
        }
      } else {
        LogUtils.warn(TAG, "User value was null.");
      }
    });

    updateUI();

    mFragmentManager = getSupportFragmentManager();
    mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

    // start up friend list fragment (which will handle query for friends)
    if (mActionBar != null) {
      mActionBar.setDisplayShowHomeEnabled(true);
    }

    // load up the friends fragment
    Fragment fragment = FriendListFragment.newInstance(userId);
    FragmentTransaction transaction = mFragmentManager.beginTransaction();
    transaction.replace(R.id.main_fragment_container, fragment, "FRIEND_LIST_FRAGMENT");
    transaction.addToBackStack(null);
    transaction.commit();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {

    LogUtils.debug(TAG, "++onCreateOptionsMenu(Menu)");
    getMenuInflater().inflate(R.menu.menu_main, menu);
    mSettingsMenuItem = menu.findItem(R.id.menu_item_settings);
    mManageUserMenuItem = menu.findItem(R.id.menu_item_manage);
    return true;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    LogUtils.debug(TAG, "++onDestroy()");
    if (mUserQuery != null && mUserValueListener != null) {
      mUserQuery.removeEventListener(mUserValueListener);
    }

    mUser = null;
    if (mTimer != null) {
      LogUtils.debug(TAG, "Shutting down location timer.");
      mTimer.cancel();
      mTimer.purge();
      mTimer = null;
    }
  }

  @Override
  public void onNoFriends() {

    LogUtils.debug(TAG, "++onNoFriends()");
    Snackbar.make(findViewById(R.id.activity_main), getString(R.string.no_friends), Snackbar.LENGTH_LONG).show();
    hideProgressDialog();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    LogUtils.debug(TAG, "++onOptionsItemSelected(MenuItem)");
    switch (item.getItemId()) {
      case android.R.id.home:
        if (mFragmentManager.getBackStackEntryCount() > 0) {
          mFragmentManager.popBackStack();
        } else {
          mActionBar.setDisplayHomeAsUpEnabled(false);
        }

        if (mSettingsMenuItem != null) {
          mSettingsMenuItem.setVisible(true);
        }

        if (mManageUserMenuItem != null) {
          mManageUserMenuItem.setVisible(true);
        }

        mActionBar.setSubtitle("");
        toggleUI(View.VISIBLE);
        if (mUser.IsSharing) {
          initializeTimerTask();
        }
        return true;
      case R.id.menu_item_manage:
        if (mActionBar != null) {
          mActionBar.setDisplayHomeAsUpEnabled(true);
          mActionBar.setSubtitle("Manage");
        }

        if (mManageUserMenuItem != null) {
          mManageUserMenuItem.setVisible(false);
        }

        toggleUI(View.INVISIBLE);

        Fragment manageUserFragment = ManageUserFragment.newInstance(mUser.UserId);
        FragmentTransaction manageUserTransaction = mFragmentManager.beginTransaction();
        manageUserTransaction.replace(R.id.main_fragment_container, manageUserFragment, "MANAGE_USER_FRAGMENT");
        manageUserTransaction.addToBackStack(null);
        manageUserTransaction.commit();
        return true;
      case R.id.menu_item_settings:
        if (mActionBar != null) {
          mActionBar.setDisplayHomeAsUpEnabled(true);
          mActionBar.setSubtitle("Settings");
        }

        if (mSettingsMenuItem != null) {
          mSettingsMenuItem.setVisible(false);
        }

        displayPreferences();
        return true;
      case R.id.menu_item_logout:
        AlertDialog dialog = new AlertDialog.Builder(this)
          .setMessage(R.string.logout_message)
          .setPositiveButton(android.R.string.yes, (dialog1, which) -> {

            // sign out of firebase
            FirebaseAuth.getInstance().signOut();

            // sign out of google, if necessary
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
              .requestIdToken(getString(R.string.default_web_client_id))
              .requestEmail()
              .build();
            GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);
            googleSignInClient.signOut().addOnCompleteListener(this, task -> {

              // return to sign-in activity
              startActivity(new Intent(getApplicationContext(), SignInActivity.class));
              finish();
            });
          })
          .setNegativeButton(android.R.string.no, null)
          .create();
        dialog.show();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public void onPopulated(int size) {

    LogUtils.debug(TAG, "++onPopulated(%1d)", size);
    if (mActionBar != null) {
      mActionBar.setDisplayHomeAsUpEnabled(false);
    }

    hideProgressDialog();
  }

  @Override
  public void onPreferenceChanged() {

    LogUtils.debug(TAG, "++onPreferenceChanged()");
    updateUser();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

    LogUtils.debug(TAG, "++onRequestPermissionResult(int, String[], int[])");
    if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
      if (grantResults.length <= 0) {
        LogUtils.debug(TAG, "User interaction was cancelled.");
      } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        mUser.IsSharing = true;
        PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean(UserPreferencesFragment.KEY_GET_SHARING_SETTING, mUser.IsSharing)
          .apply();
        FirebaseDatabase.getInstance().getReference().child(User.USERS_ROOT).child(mUser.UserId).child("IsSharing").setValue(mUser.IsSharing);
        startLocationTask();
      } else {
        Snackbar.make(
          findViewById(R.id.activity_main),
          getString(R.string.permission_denied_explanation),
          Snackbar.LENGTH_INDEFINITE).setAction(
          getString(R.string.settings),
          view -> {
            // build intent that displays the app settings screen.
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null);
            intent.setData(uri);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
          }).show();
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    LogUtils.debug(TAG, "++onResume()");
    if (mUser != null && mUser.IsSharing) {
      startLocationTask();
    }
  }

  @Override
  public void onSelected(String friendId) {

    LogUtils.debug(TAG, "++onSelected(%1s)", friendId);
    if (mActionBar != null) {
      mActionBar.setDisplayHomeAsUpEnabled(true);
      mActionBar.setSubtitle("");
    }

    if (mSettingsMenuItem != null) {
      mSettingsMenuItem.setVisible(false);
    }

    if (mManageUserMenuItem != null) {
      mManageUserMenuItem.setVisible(false);
    }

    Fragment fragment = MappingFragment.newInstance(mUser.UserId);
    FragmentTransaction transaction = mFragmentManager.beginTransaction();
    transaction.replace(R.id.main_fragment_container, fragment, "MAPPING_FRAGMENT");
    transaction.addToBackStack(null);
    transaction.commit();
  }

  private boolean checkPermissions() {

    LogUtils.debug(TAG, "++checkPermissions()");
    int permissionState = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
    return permissionState == PackageManager.PERMISSION_GRANTED;
  }

  private void displayPreferences() {

    hideProgressDialog();
    toggleUI(View.INVISIBLE);
    Fragment preferenceFragment = new UserPreferencesFragment();
    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
    transaction.replace(R.id.main_fragment_container, preferenceFragment, "USER_PREFERENCE_FRAGMENT");
    transaction.addToBackStack(null);
    transaction.commit();
  }

  @SuppressWarnings("MissingPermission")
  private void getLastLocation() {

    LogUtils.debug(TAG, "++getLastLocation()");
    mFusedLocationClient.getLastLocation().addOnCompleteListener(
      this,
      task -> {
        if (task.isSuccessful() && task.getResult() != null) {
          mLastLocation = task.getResult();
          mTimeStampTextView.setText(DateUtils.formatDateForDisplay(mLastLocation.getTime()));

          UserLocation location = new UserLocation();
          location.TimeStamp = mLastLocation.getTime();
          location.Latitude = mLastLocation.getLatitude();
          location.Longitude = mLastLocation.getLongitude();
          mUser.LocationList.put(String.valueOf(mLastLocation.getTime()), location);

          String queryPath = User.USERS_ROOT + "/" + mUser.UserId + "/" + UserLocation.ROOT;
          LogUtils.debug(TAG, "QueryPath: %s", queryPath);
          FirebaseDatabase.getInstance().getReference().child(queryPath).child(String.valueOf(mLastLocation.getTime())).setValue(location.toMap());
        } else {
          LogUtils.warn(TAG, "getLastLocation:exception", task.getException());
        }
      });
  }

  private void initializeTimerTask() {

    LogUtils.debug(TAG, "++initializeTimeTask()");
    mTimerTask = new TimerTask() {
      public void run() {
        mHandler.post(() -> getLastLocation());
      }
    };
  }


  private void requestPermissions() {

    LogUtils.debug(TAG, "++requestPermissions()");
    boolean shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(
      this,
      android.Manifest.permission.ACCESS_COARSE_LOCATION);
    if (shouldProvideRationale) {
      Snackbar.make(
        findViewById(R.id.activity_main),
        getString(R.string.permission_rationale),
        Snackbar.LENGTH_INDEFINITE).setAction(
        getString(android.R.string.ok),
        view -> startLocationPermissionRequest()).show();
    } else {
      startLocationPermissionRequest();
    }
  }

  private void startLocationPermissionRequest() {

    LogUtils.debug(TAG, "++startLocationPermissionRequest()");
    ActivityCompat.requestPermissions(
      this,
      new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION},
      REQUEST_PERMISSIONS_REQUEST_CODE);
  }

  private void startLocationTask() {

    LogUtils.debug(TAG, "++startLocationTask()");
    if (mTimer != null) {
      LogUtils.debug(TAG, "Shutting down location timer.");
      mTimer.cancel();
      mTimer.purge();
      mTimer = null;
    }

    mTimer = new Timer();
    initializeTimerTask();
    LogUtils.debug(TAG, "Starting location timer.");
    if (mUser != null && mUser.Frequency > 0) {
      long timerDelay = mUser.Frequency * (60 * 1000);
      mTimer.schedule(mTimerTask, (500), timerDelay);
    } else {
      LogUtils.warn(TAG, "User not initialized, starting timer with default delay.");
      mTimer.schedule(mTimerTask, (500), (60 * 1000));
    }
  }

  private void toggleUI(int visibility) {

    LogUtils.debug(TAG, "++toggleUI(%d)", visibility);
    mSharingActionButton.setVisibility(visibility);
    mStatusHeaderTextView.setVisibility(visibility);
    mStatusTextView.setVisibility(visibility);
    mTimeStampHeaderTextView.setVisibility(visibility);
    mTimeStampTextView.setVisibility(visibility);
  }

  private void updateUI() {

    LogUtils.debug(TAG, "++updateUI()");
    if (mUser != null) {
      if (mUser.IsSharing) {
        mStatusTextView.setTextColor(Color.GREEN);
        mStatusTextView.setTypeface(null, Typeface.NORMAL);
        mStatusTextView.setText(getString(R.string.status_sharing));
        mTimeStampTextView.setTypeface(null, Typeface.NORMAL);
        mTimeStampTextView.setText(DateUtils.formatDateForDisplay(mUser.get_LatestTimeStamp()));
        mSharingActionButton.setImageResource(R.drawable.ic_sharing_on_dark);
      } else {
        mStatusTextView.setTextColor(Color.RED);
        mStatusTextView.setTypeface(null, Typeface.NORMAL);
        mStatusTextView.setText(getString(R.string.status_not_sharing));
        mTimeStampTextView.setTypeface(null, Typeface.NORMAL);
        mTimeStampTextView.setText(DateUtils.formatDateForDisplay(mUser.get_LatestTimeStamp()));
        mSharingActionButton.setImageResource(R.drawable.ic_sharing_off_dark);
      }
    } else {
      mStatusTextView.setTextColor(getColor(android.R.color.primary_text_dark));
      mStatusTextView.setTypeface(null, Typeface.ITALIC);
      mStatusTextView.setText(R.string.pending);
      mTimeStampTextView.setTypeface(null, Typeface.ITALIC);
      mTimeStampTextView.setText(R.string.pending);
      mSharingActionButton.setImageResource(R.drawable.ic_sharing_off_dark);
    }
  }

  private void updateUser() {

    LogUtils.debug(TAG, "++updateUser()");
    if (mUser != null) {
      SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
      if (sharedPreferences.contains(UserPreferencesFragment.KEY_GET_HISTORY_SETTING)) {
        mUser.History = Integer.parseInt(sharedPreferences.getString(UserPreferencesFragment.KEY_GET_HISTORY_SETTING, "-1"));
      }

      if (sharedPreferences.contains(UserPreferencesFragment.KEY_GET_FREQUENCY_SETTING)) {
        mUser.Frequency = Integer.parseInt(sharedPreferences.getString(UserPreferencesFragment.KEY_GET_FREQUENCY_SETTING, "-1"));
      }

      // check sharing status last since it depends on frequency value
      if (sharedPreferences.contains(UserPreferencesFragment.KEY_GET_SHARING_SETTING)) {
        mUser.IsSharing = sharedPreferences.getBoolean(UserPreferencesFragment.KEY_GET_SHARING_SETTING, false);
      }

      if (mUser.IsSharing) {
        FirebaseDatabase.getInstance().getReference().child(User.USERS_ROOT).child(mUser.UserId).child("IsSharing").setValue(mUser.IsSharing);
        startLocationTask();
      }
    }

    updateUI();
  }
}
