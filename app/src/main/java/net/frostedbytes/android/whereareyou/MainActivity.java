package net.frostedbytes.android.whereareyou;

import android.Manifest;
import android.Manifest.permission;
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
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
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
import java.util.Locale;
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

public class MainActivity extends BaseActivity implements
  NavigationView.OnNavigationItemSelectedListener,
  FriendListFragment.OnFriendListListener,
  UserPreferencesFragment.OnPreferencesListener,
  ManageUserFragment.OnManageUserListener {

  private static final String TAG = MainActivity.class.getSimpleName();

  private static final int LOCATION_PERMISSION_REQUEST_CODE = 34;
  private static final int CONTACTS_PERMISSION_REQUEST_CODE = 35;

  private DrawerLayout mDrawerLayout;
  private ActionBar mActionBar;
  private FragmentManager mFragmentManager;
  private FloatingActionButton mSharingButton;
  private TextView mStatusTextView;
  private TextView mTimeStampTextView;

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

    mDrawerLayout = findViewById(R.id.main_drawer_layout);
    Toolbar toolbar = findViewById(R.id.main_toolbar);
    mSharingButton = findViewById(R.id.main_button_share_location);
    mStatusTextView = findViewById(R.id.main_text_status);
    mTimeStampTextView = findViewById(R.id.main_text_timestamp);

    // setup tool bar
    setSupportActionBar(toolbar);
    mActionBar = getSupportActionBar();

    ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
      this,
      mDrawerLayout,
      toolbar,
      R.string.navigation_drawer_open,
      R.string.navigation_drawer_close);
    mDrawerLayout.addDrawerListener(toggle);
    toggle.syncState();

    NavigationView navigationView = findViewById(R.id.main_navigation_view);
    navigationView.setNavigationItemSelectedListener(this);

    // get parameters from previous activity
    String userId = getIntent().getStringExtra(BaseActivity.ARG_USER_ID);
    String userName = getIntent().getStringExtra(BaseActivity.ARG_USER_NAME);

    // look for user in database
    mUserQuery = FirebaseDatabase.getInstance().getReference().child(User.USERS_ROOT).child(userId);
    mUserValueListener = new ValueEventListener() {

      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {

        LogUtils.debug(TAG, "++onDataChange()");
        mUser = dataSnapshot.getValue(User.class);
        if (mUser != null) {
          // found user, update our copy
          mUser.UserId = dataSnapshot.getKey();
          LogUtils.debug(TAG, "Data changed for user: %s", mUser.UserId);
        } else {
          // user not found, create a new one in the database
          mUser = new User();
          mUser.UserId = userId;
          mUser.FullName = userName;
          mUserQuery.getRef().setValue(mUser);
        }

        // make sure this user object has the preferences
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (sharedPreferences.contains(UserPreferencesFragment.KEY_GET_HISTORY_SETTING)) {
          mUser.History = Integer.parseInt(sharedPreferences.getString(UserPreferencesFragment.KEY_GET_HISTORY_SETTING, "-1"));
        } else {
          sharedPreferences.edit().putString(UserPreferencesFragment.KEY_GET_HISTORY_SETTING, String.valueOf(mUser.History)).apply();
        }

        if (sharedPreferences.contains(UserPreferencesFragment.KEY_GET_FREQUENCY_SETTING)) {
          mUser.Frequency = Integer.parseInt(sharedPreferences.getString(UserPreferencesFragment.KEY_GET_FREQUENCY_SETTING, "-1"));
        } else {
          sharedPreferences.edit().putString(UserPreferencesFragment.KEY_GET_FREQUENCY_SETTING, String.valueOf(mUser.Frequency)).apply();
        }
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {

        LogUtils.debug(TAG, "++onCancelled(DatabaseError)");
        LogUtils.error(TAG, databaseError.getMessage());
      }
    };
    mUserQuery.addValueEventListener(mUserValueListener);

    mSharingButton.setOnClickListener(view -> {

      if (mUser != null) {
        if (mUser.IsSharing) { // turn off location sharing
          mUser.IsSharing = false;
          FirebaseDatabase.getInstance().getReference().child(User.USERS_ROOT).child(mUser.UserId).child("IsSharing").setValue(mUser.IsSharing);
          stopTimer();
        } else { // turn on location sharing, after checking permissions
          if (isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            mUser.IsSharing = true;
            FirebaseDatabase.getInstance().getReference().child(User.USERS_ROOT).child(mUser.UserId).child("IsSharing").setValue(mUser.IsSharing);
            startLocationTask();
          } else {
            requestPermission(getString(R.string.permission_locale_rationale), permission.ACCESS_COARSE_LOCATION, LOCATION_PERMISSION_REQUEST_CODE);
          }
        }

        updateUI();
      } else {
        LogUtils.warn(TAG, "User value was null.");
      }
    });

    updateUI();

    mFragmentManager = getSupportFragmentManager();
    mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

    // load up the friends fragment
    Fragment fragment = FriendListFragment.newInstance(userId);
    FragmentTransaction transaction = mFragmentManager.beginTransaction();
    transaction.replace(R.id.main_fragment_container, fragment, "FRIEND_LIST_FRAGMENT");
    transaction.addToBackStack(null);
    transaction.commit();
  }

  @Override
  public void onBackPressed() {

    LogUtils.debug(TAG, "++onBackPressed()");
    if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
      mDrawerLayout.closeDrawer(GravityCompat.START);
    } else {
      if (getSupportFragmentManager().getBackStackEntryCount() == 1) {
        finish();
      } else {
        super.onBackPressed();
      }
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    LogUtils.debug(TAG, "++onDestroy()");
    if (mUserQuery != null && mUserValueListener != null) {
      mUserQuery.removeEventListener(mUserValueListener);
    }

    mUser = null;
    stopTimer();
  }

  @Override
  public void onDeleteFriend(String friendId) {

    LogUtils.debug(TAG, "++onDeleteFriend(String)");
    LogUtils.debug(TAG, "Deleting %s from %s friend list.", friendId, mUser.UserId);
  }

  @SuppressWarnings("StatementWithEmptyBody")
  @Override
  public boolean onNavigationItemSelected(@NonNull MenuItem item) {

    LogUtils.debug(TAG, "++onNavigationItemSelected(%s)", item.getTitle());
    switch (item.getItemId()) {
      case R.id.navigation_menu_home:
        if (mFragmentManager.getBackStackEntryCount() > 0) {
          mFragmentManager.popBackStack();
        }

        if (mActionBar != null) {
          mActionBar.setSubtitle("");
        }
        break;
      case R.id.navigation_menu_manage:
        if (isPermissionGranted(permission.READ_CONTACTS)) {
          if (mActionBar != null) {
            mActionBar.setSubtitle("Manage");
          }

          Fragment manageUserFragment = ManageUserFragment.newInstance(mUser.UserId);
          FragmentTransaction manageUserTransaction = mFragmentManager.beginTransaction();
          manageUserTransaction.replace(R.id.main_fragment_container, manageUserFragment, "MANAGE_USER_FRAGMENT");
          manageUserTransaction.addToBackStack(null);
          manageUserTransaction.commit();
        } else {
          requestPermission(getString(R.string.permission_contacts_rationale), permission.READ_CONTACTS, CONTACTS_PERMISSION_REQUEST_CODE);
        }
        break;
      case R.id.navigation_menu_setting:
        if (mActionBar != null) {
          mActionBar.setSubtitle("Settings");
        }

        displayPreferences();
        break;
      case R.id.navigation_menu_logout:
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
        break;
    }

    mDrawerLayout.closeDrawer(GravityCompat.START);
    return true;
  }

  @Override
  public void onNoFriends() {

    LogUtils.debug(TAG, "++onNoFriends()");
    Snackbar.make(findViewById(R.id.main_drawer_layout), getString(R.string.no_friends), Snackbar.LENGTH_LONG).show();
    hideProgressDialog();
  }

  @Override
  public void onPause() {
    super.onPause();

    LogUtils.debug(TAG, "++onPause()");
    // TODO: might need to halt location timer during suspension to save battery; need testing
  }

  @Override
  public void onPopulated(int size) {

    LogUtils.debug(TAG, "++onPopulated(%1d)", size);
    hideProgressDialog();
  }

  @Override
  public void onPreferenceChanged() {

    LogUtils.debug(TAG, "++onPreferenceChanged()");
    if (mUser != null) {
      SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
      if (sharedPreferences.contains(UserPreferencesFragment.KEY_GET_HISTORY_SETTING)) {
        mUser.History = Integer.parseInt(sharedPreferences.getString(UserPreferencesFragment.KEY_GET_HISTORY_SETTING, "-1"));
        FirebaseDatabase.getInstance().getReference().child(User.USERS_ROOT).child(mUser.UserId).child("History").setValue(mUser.History);
      }

      if (sharedPreferences.contains(UserPreferencesFragment.KEY_GET_FREQUENCY_SETTING)) {
        mUser.Frequency = Integer.parseInt(sharedPreferences.getString(UserPreferencesFragment.KEY_GET_FREQUENCY_SETTING, "-1"));
      }
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

    LogUtils.debug(TAG, "++onRequestPermissionResult(int, String[], int[])");
    if (requestCode == LOCATION_PERMISSION_REQUEST_CODE || requestCode == CONTACTS_PERMISSION_REQUEST_CODE) {
      // TODO: may need to handle multiple grantResults
      if (grantResults.length <= 0) {
        LogUtils.debug(TAG, "User interaction was cancelled.");
      } else if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        mUser.IsSharing = true;
        FirebaseDatabase.getInstance().getReference().child(User.USERS_ROOT).child(mUser.UserId).child("IsSharing").setValue(mUser.IsSharing);
        startLocationTask();
        updateUI();
      } else if (requestCode == CONTACTS_PERMISSION_REQUEST_CODE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        if (mActionBar != null) {
          mActionBar.setSubtitle("Manage");
        }

        Fragment manageUserFragment = ManageUserFragment.newInstance(mUser.UserId);
        FragmentTransaction manageUserTransaction = mFragmentManager.beginTransaction();
        manageUserTransaction.replace(R.id.main_fragment_container, manageUserFragment, "MANAGE_USER_FRAGMENT");
        manageUserTransaction.addToBackStack(null);
        manageUserTransaction.commit();
      } else {
        Snackbar.make(
          findViewById(R.id.main_drawer_layout),
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
    } else {
      LogUtils.debug(TAG, "Skipping request: %d", requestCode);
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
      mActionBar.setSubtitle("");
    }

    Fragment fragment = MappingFragment.newInstance(mUser.UserId, friendId);
    FragmentTransaction transaction = mFragmentManager.beginTransaction();
    transaction.replace(R.id.main_fragment_container, fragment, "MAPPING_FRAGMENT");
    transaction.addToBackStack(null);
    transaction.commit();
  }

  private void displayPreferences() {

    hideProgressDialog();
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

          String queryPath = User.USERS_ROOT + "/" + mUser.UserId + "/" + User.LOCATION_LIST_ROOT;
          LogUtils.debug(TAG, "QueryPath: %s", queryPath);
          FirebaseDatabase.getInstance().getReference().child(queryPath).child(String.valueOf(location.TimeStamp)).setValue(location.toMap());
        } else {
          LogUtils.warn(TAG, "getLastLocation:exception", task.getException());
        }
      });
  }

  private boolean isPermissionGranted(String permission) {

    LogUtils.debug(TAG, "++isPermissionGranted(%s)", permission);
    int permissionState = ActivityCompat.checkSelfPermission(this, permission);
    return permissionState == PackageManager.PERMISSION_GRANTED;
  }

  private void requestPermission(String permission, String rationale, int requestCode) {

    LogUtils.debug(TAG, "++requestPermission(%s, %s, %d)", permission, rationale, requestCode);
    boolean shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
    if (shouldProvideRationale) {
      Snackbar.make(
        findViewById(R.id.main_drawer_layout),
        rationale,
        Snackbar.LENGTH_INDEFINITE).setAction(
        getString(android.R.string.ok),
        view -> startPermissionRequest(permission, requestCode)).show();
    } else {
      startPermissionRequest(permission, requestCode);
    }
  }

  private void startPermissionRequest(String permission, int requestCode) {

    LogUtils.debug(TAG, "++startPermissionRequest(%s, %d)", permission, requestCode);
    ActivityCompat.requestPermissions(
      this,
      new String[]{permission},
      requestCode);
  }

  private void startLocationTask() {

    LogUtils.debug(TAG, "++startLocationTask()");
    stopTimer();
    mTimer = new Timer();
    mTimerTask = new TimerTask() {

      public void run() {
        mHandler.post(() -> getLastLocation());
      }
    };

    LogUtils.debug(TAG, "Starting location timer.");
    if (mUser != null && mUser.Frequency > 0) {
      long timerDelay = mUser.Frequency * (60 * 1000);
      mTimer.schedule(mTimerTask, (500), timerDelay);
    } else {
      LogUtils.warn(TAG, "User not initialized, starting timer with default delay.");
      mTimer.schedule(mTimerTask, (500), (60 * 1000));
    }
  }

  private void stopTimer() {

    LogUtils.debug(TAG, "++stopTimer()");
    if (mTimerTask != null) {
      mTimerTask.cancel();
    }

    if (mTimer != null) {
      LogUtils.debug(TAG, "Shutting down location timer.");
      mTimer.cancel();
      mTimer.purge();
      mTimer = null;
    }
  }

  private void updateUI() {

    LogUtils.debug(TAG, "++updateUI()");
    if (mUser != null) {
      mStatusTextView.setTypeface(null, Typeface.NORMAL);
      mTimeStampTextView.setTypeface(null, Typeface.NORMAL);
      mTimeStampTextView.setText(
        String.format(
          Locale.ENGLISH,
          "%s %s",
          getString(R.string.timestamp_header),
          DateUtils.formatDateForDisplay(mUser.get_LatestTimeStamp())));
      if (mUser.IsSharing) {
        mSharingButton.setImageResource(R.drawable.ic_sharing_on_dark);
        mStatusTextView.setTextColor(Color.GREEN);
        mStatusTextView.setText(
          String.format(
            Locale.ENGLISH,
            "%s %s",
            getString(R.string.status_header),
            getString(R.string.status_sharing)));
      } else {
        mSharingButton.setImageResource(R.drawable.ic_sharing_off_dark);
        mStatusTextView.setTextColor(Color.RED);
        mStatusTextView.setText(
          String.format(
            Locale.ENGLISH,
            "%s %s",
            getString(R.string.status_header),
            getString(R.string.status_not_sharing)));
      }
    } else {
      mSharingButton.setImageResource(R.drawable.ic_sharing_off_dark);
      mStatusTextView.setTextColor(getColor(android.R.color.primary_text_dark));
      mStatusTextView.setTypeface(null, Typeface.ITALIC);
      mStatusTextView.setText(
        String.format(
          Locale.ENGLISH,
          "%s %s",
          getString(R.string.status_header),
          getString(R.string.pending)));
      mTimeStampTextView.setTypeface(null, Typeface.ITALIC);
      mTimeStampTextView.setText(
        String.format(
          Locale.ENGLISH,
          "%s %s",
          getString(R.string.timestamp_header),
          getString(R.string.pending)));
    }
  }
}
