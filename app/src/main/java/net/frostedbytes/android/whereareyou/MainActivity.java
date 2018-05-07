/*
 * Copyright 2018 Ryan Ward
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package net.frostedbytes.android.whereareyou;

import android.Manifest;
import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import net.frostedbytes.android.whereareyou.fragments.FriendsPageFragment;
import net.frostedbytes.android.whereareyou.fragments.MainListFragment;
import net.frostedbytes.android.whereareyou.fragments.ManageUsersFragment;
import net.frostedbytes.android.whereareyou.fragments.MappingFragment;
import net.frostedbytes.android.whereareyou.fragments.RequestsPageFragment;
import net.frostedbytes.android.whereareyou.fragments.UserPreferencesFragment;
import net.frostedbytes.android.whereareyou.models.User;
import net.frostedbytes.android.whereareyou.models.UserLocation;
import net.frostedbytes.android.whereareyou.utils.DateUtils;
import net.frostedbytes.android.whereareyou.utils.LogUtils;
import net.frostedbytes.android.whereareyou.utils.PathUtils;

public class MainActivity extends BaseActivity implements
  NavigationView.OnNavigationItemSelectedListener,
  MainListFragment.OnMainListListener,
  UserPreferencesFragment.OnPreferencesListener,
  FriendsPageFragment.OnFriendListListener,
  RequestsPageFragment.OnRequestListListener {

  private static final String TAG = MainActivity.class.getSimpleName();

  private static final int LOCATION_PERMISSION_REQUEST_CODE = 34;
  private static final int CONTACTS_PERMISSION_REQUEST_CODE = 35;

  private DrawerLayout mDrawerLayout;
  private NavigationView mNavigationView;
  private FloatingActionButton mSharingButton;
  private TextView mStatusTextView;
  private TextView mTimeStampTextView;

  private Query mQuery;
  private ListenerRegistration mListenerRegistration;

  private User mUser;

  private FusedLocationProviderClient mFusedLocationClient;

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
    getSupportFragmentManager().addOnBackStackChangedListener(() -> {
      Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_fragment_container);
      if (fragment != null) {
        updateTitleAndDrawer(fragment);
      }
    });

    ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
      this,
      mDrawerLayout,
      toolbar,
      R.string.navigation_drawer_open,
      R.string.navigation_drawer_close);
    mDrawerLayout.addDrawerListener(toggle);
    toggle.syncState();

    mNavigationView = findViewById(R.id.main_navigation_view);
    mNavigationView.setNavigationItemSelectedListener(this);

    // get parameters from previous activity
    String userId = getIntent().getStringExtra(BaseActivity.ARG_USER_ID);
    String userName = getIntent().getStringExtra(BaseActivity.ARG_USER_NAME);
    String email = getIntent().getStringExtra(BaseActivity.ARG_EMAIL);

    View navigationHeaderView = mNavigationView.inflateHeaderView(R.layout.main_navigation_header);
    TextView navigationFullName = navigationHeaderView.findViewById(R.id.navigation_text_full_name);
    navigationFullName.setText(userName);
    TextView navigationEmail = navigationHeaderView.findViewById(R.id.navigation_text_email);
    navigationEmail.setText(email);

    // look for user in database
    mQuery = FirebaseFirestore.getInstance()
      .collection(User.USERS_ROOT)
      .whereEqualTo("UserId", userId);
    mListenerRegistration = mQuery.addSnapshotListener(new EventListener<QuerySnapshot>() {

      @Override
      public void onEvent(@Nullable QuerySnapshot snapshot, @Nullable FirebaseFirestoreException e) {

        if (e != null) {
          LogUtils.error(TAG, "%s", e.getMessage());
          return;
        }

        if (snapshot == null) {
          LogUtils.error(TAG, "User query snapshot is null: %s", mQuery);
          return;
        }

        List<User> users = snapshot.toObjects(User.class);
        if (users.isEmpty()) {
          LogUtils.debug(TAG, "No such user: %s", userId);
          mUser = new User();
          mUser.UserId = userId;
          mUser.Email = email;
          mUser.FullName = userName;
          FirebaseFirestore.getInstance().collection(User.USERS_ROOT).document(userId).set(mUser);
        } else {
          for (DocumentSnapshot document : snapshot.getDocuments()) {
            mUser = document.toObject(User.class);
            if (mUser != null) {
              mUser.UserId = document.getId();
              break;
            }
          }
        }
      }
    });

    mSharingButton.setOnClickListener(view -> {

      if (mUser != null && !mUser.UserId.equals(BaseActivity.DEFAULT_ID)) {
        String isSharingPath = PathUtils.combine(User.USERS_ROOT, mUser.UserId);
        if (mUser.IsSharing) { // turn off location sharing
          mUser.IsSharing = false;
          FirebaseFirestore.getInstance().document(isSharingPath).update("IsSharing", mUser.IsSharing);
          stopTimer();
        } else { // turn on location sharing, after checking permissions
          if (isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            mUser.IsSharing = true;
            FirebaseFirestore.getInstance().document(isSharingPath).update("IsSharing", mUser.IsSharing);
            startLocationTask();
          } else {
            requestPermission(getString(R.string.permission_locale_rationale), permission.ACCESS_COARSE_LOCATION, LOCATION_PERMISSION_REQUEST_CODE);
          }
        }

        updateUI();
      } else {
        LogUtils.warn(TAG, "User value was null or unexpected: %s", mUser != null ? mUser.UserId : "");
      }
    });

    updateUI();

    mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

    // load up the friends fragment
    replaceFragment(MainListFragment.newInstance(userId));
  }

  @Override
  public void onAcceptFriend(String userId, String friendId) {

    LogUtils.debug(TAG, "++onAcceptFriend(%s, %s)", userId, friendId);
    mUser.FriendList.get(friendId).IsAccepted = true;
    mUser.FriendList.get(friendId).IsDeclined = false;
    mUser.FriendList.get(friendId).IsPending = false;
    String queryPath = PathUtils.combine(User.USERS_ROOT, userId, User.FRIEND_LIST);
    FirebaseFirestore.getInstance().collection(queryPath).document(friendId).set(mUser.FriendList.get(friendId));
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
  public void onDeclineFriend(String userId, String friendId) {

    LogUtils.debug(TAG, "++onDeclineFriend(%s, %s)", userId, friendId);
    onDeleteFriend(userId, friendId);
  }

  @Override
  public void onDeleteFriend(String userId, String friendId) {

    LogUtils.debug(TAG, "++onDeleteFriend(%s, %s)", userId, friendId);
    String queryPath = PathUtils.combine(User.USERS_ROOT, mUser.UserId, User.FRIEND_LIST);
    FirebaseFirestore.getInstance().collection(queryPath).document(friendId).delete();
  }

  @Override
  public void onDeleteRequest(String userId, String friendId) {

    LogUtils.debug(TAG, "++onDeleteRequest(%s, %s)", userId, friendId);
    onDeleteFriend(userId, friendId);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    LogUtils.debug(TAG, "++onDestroy()");
    if (mListenerRegistration != null) {
      mListenerRegistration.remove();
      mListenerRegistration = null;
    }

    mUser = null;
    stopTimer();
  }

  @SuppressWarnings("StatementWithEmptyBody")
  @Override
  public boolean onNavigationItemSelected(@NonNull MenuItem item) {

    LogUtils.debug(TAG, "++onNavigationItemSelected(%s)", item.getTitle());
    switch (item.getItemId()) {
      case R.id.navigation_menu_home:
        Fragment homeFragment = MainListFragment.newInstance(mUser.UserId);
        replaceFragment(homeFragment);
        updateTitleAndDrawer(homeFragment);
        break;
      case R.id.navigation_menu_manage:
        if (isPermissionGranted(permission.READ_CONTACTS)) {
          Fragment manageFragment = ManageUsersFragment.newInstance(mUser.UserId);
          replaceFragment(manageFragment);
          updateTitleAndDrawer(manageFragment);
        } else {
          requestPermission(getString(R.string.permission_contacts_rationale), permission.READ_CONTACTS, CONTACTS_PERMISSION_REQUEST_CODE);
        }
        break;
      case R.id.navigation_menu_preferences:
        displayPreferences();
        break;
      case R.id.navigation_menu_logout:
        @SuppressLint("RestrictedApi") AlertDialog dialog = new AlertDialog.Builder(this)
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
      if (sharedPreferences.contains(UserPreferencesFragment.KEY_GET_FREQUENCY_SETTING)) {
        mUser.Frequency = Integer.parseInt(sharedPreferences.getString(UserPreferencesFragment.KEY_GET_FREQUENCY_SETTING, "-1"));
      }
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

    LogUtils.debug(TAG, "++onRequestPermissionResult(int, String[], int[])");
    if (requestCode == LOCATION_PERMISSION_REQUEST_CODE || requestCode == CONTACTS_PERMISSION_REQUEST_CODE) {
      if (grantResults.length <= 0) {
        LogUtils.debug(TAG, "User interaction was cancelled.");
      } else if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        mUser.IsSharing = true;
        String isSharingPath = PathUtils.combine(User.USERS_ROOT, mUser.UserId);
        FirebaseFirestore.getInstance().document(isSharingPath).update("IsSharing", mUser.IsSharing);
        startLocationTask();
        updateUI();
      } else if (requestCode == CONTACTS_PERMISSION_REQUEST_CODE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        replaceFragment(ManageUsersFragment.newInstance(mUser.UserId));
      } else {
        Snackbar.make(
          findViewById(R.id.main_drawer_layout),
          getString(R.string.permission_denied_explanation),
          Snackbar.LENGTH_INDEFINITE).setAction(
          getString(R.string.preferences),
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
    replaceFragment(MappingFragment.newInstance(mUser.UserId, friendId));
  }

  private void displayPreferences() {

    hideProgressDialog();
    Fragment preferencesFragment = new UserPreferencesFragment();
    replaceFragment(preferencesFragment);
    updateTitleAndDrawer(preferencesFragment);
  }

  @SuppressWarnings("MissingPermission")
  private void getLastLocation() {

    LogUtils.debug(TAG, "++getLastLocation()");
    mFusedLocationClient.getLastLocation().addOnSuccessListener(location -> {

      if (location != null) { // logic to handle location object
        mTimeStampTextView.setText(DateUtils.formatDateForDisplay(location.getTime()));

        // create new location object
        UserLocation userLocation = new UserLocation();
        userLocation.TimeStamp = location.getTime();
        userLocation.Latitude = location.getLatitude();
        userLocation.Longitude = location.getLongitude();

        // add location object to users location list (shouldn't this trigger an update?)
        mUser.LocationList.put(String.valueOf(location.getTime()), userLocation);

        // add location to firestore
        String queryPath = PathUtils.combine(User.USERS_ROOT, mUser.UserId, User.LOCATION_LIST);
        LogUtils.debug(TAG, "QueryPath: %s", queryPath);
        FirebaseFirestore.getInstance().collection(queryPath).document(String.valueOf(userLocation.TimeStamp)).set(userLocation);
      } else {
        LogUtils.warn(TAG, "getLastLocation was null.");
      }
    })
    .addOnFailureListener(e -> LogUtils.error(TAG, "getLastLocation failed: %s", e.getMessage()));
  }

  private boolean isPermissionGranted(String permission) {

    LogUtils.debug(TAG, "++isPermissionGranted(%s)", permission);
    int permissionState = ActivityCompat.checkSelfPermission(this, permission);
    return permissionState == PackageManager.PERMISSION_GRANTED;
  }

  private void replaceFragment(Fragment fragment) {

    LogUtils.debug(TAG, "++replaceFragment(%s)", fragment.getTag());
    FragmentManager fragmentManager = getSupportFragmentManager();
    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
    fragmentTransaction.replace(R.id.main_fragment_container, fragment);
    fragmentTransaction.commit();
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

  private void updateTitleAndDrawer(Fragment fragment) {

    String fragmentClassName = fragment.getClass().getName();
    if (fragmentClassName.equals(ManageUsersFragment.class.getName())) {
      mNavigationView.getMenu().findItem(R.id.navigation_menu_home).setEnabled(true);
      mNavigationView.getMenu().findItem(R.id.navigation_menu_manage).setEnabled(false);
      mNavigationView.getMenu().findItem(R.id.navigation_menu_preferences).setEnabled(true);
      setTitle("Manage");
    } else if (fragmentClassName.equals(UserPreferencesFragment.class.getName())) {
      mNavigationView.getMenu().findItem(R.id.navigation_menu_home).setEnabled(true);
      mNavigationView.getMenu().findItem(R.id.navigation_menu_manage).setEnabled(true);
      mNavigationView.getMenu().findItem(R.id.navigation_menu_preferences).setEnabled(false);
      setTitle("Preferences");
    } else if (fragmentClassName.equals(MainListFragment.class.getName())) {
      mNavigationView.getMenu().findItem(R.id.navigation_menu_home).setEnabled(false);
      mNavigationView.getMenu().findItem(R.id.navigation_menu_manage).setEnabled(true);
      mNavigationView.getMenu().findItem(R.id.navigation_menu_preferences).setEnabled(true);
      setTitle(getString(R.string.app_name));
    } else {
      mNavigationView.getMenu().findItem(R.id.navigation_menu_home).setEnabled(true);
      mNavigationView.getMenu().findItem(R.id.navigation_menu_manage).setEnabled(true);
      mNavigationView.getMenu().findItem(R.id.navigation_menu_preferences).setEnabled(true);
      setTitle("");
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
          DateUtils.formatDateForDisplay(mUser.getMostRecentLocationTimeStamp())));
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
