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
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.frostedbytes.android.whereareyou.fragments.ContactsFragment;
import net.frostedbytes.android.whereareyou.fragments.FriendListFragment;
import net.frostedbytes.android.whereareyou.fragments.MappingFragment;
import net.frostedbytes.android.whereareyou.fragments.UserPreferencesFragment;
import net.frostedbytes.android.whereareyou.models.Friend;
import net.frostedbytes.android.whereareyou.models.User;
import net.frostedbytes.android.whereareyou.utils.LogUtils;
import net.frostedbytes.android.whereareyou.utils.PathUtils;

public class MainActivity extends BaseActivity implements
    NavigationView.OnNavigationItemSelectedListener,
    FriendListFragment.OnFriendListListener,
    ContactsFragment.OnContactListListener,
    UserPreferencesFragment.OnPreferencesListener {

    private static final String TAG = BASE_TAG + MainActivity.class.getSimpleName();

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 34;
    private static final int CONTACTS_PERMISSION_REQUEST_CODE = 35;

    private DrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;
    private ProgressBar mProgressBar;

    //    private Map<String, Friend> mFriendList;
    private User mUser;

    /*
        Activity Handling
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LogUtils.debug(TAG, "++onCreate(Bundle)");
        setContentView(R.layout.activity_main);

        // grab controls
        mDrawerLayout = findViewById(R.id.main_drawer_layout);
        mProgressBar = findViewById(R.id.main_progress);
        Toolbar toolbar = findViewById(R.id.main_toolbar);

        mProgressBar.setIndeterminate(true);

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
        mUser = new User();
        mUser.Id = getIntent().getStringExtra(BaseActivity.ARG_USER_ID);
        mUser.FullName = getIntent().getStringExtra(BaseActivity.ARG_USER_NAME);
        mUser.Email = getIntent().getStringExtra(BaseActivity.ARG_EMAIL);

        View navigationHeaderView = mNavigationView.inflateHeaderView(R.layout.main_navigation_header);
        TextView navigationFullName = navigationHeaderView.findViewById(R.id.navigation_text_full_name);
        navigationFullName.setText(mUser.FullName);
        TextView navigationEmail = navigationHeaderView.findViewById(R.id.navigation_text_email);
        navigationEmail.setText(mUser.Email);

        // check permission
        checkLocationPermission();
    }

    @Override
    public void onBackPressed() {

        LogUtils.debug(TAG, "++onBackPressed()");
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        } else {
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_fragment_container);
            if (fragment != null && fragment.getClass().getSimpleName().equals(FriendListFragment.class.getSimpleName())) {
                finish();
            } else {
                replaceFragment(FriendListFragment.newInstance(mUser.Id));
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        LogUtils.debug(TAG, "++onDestroy()");
        mUser = null;
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        LogUtils.debug(TAG, "++onNavigationItemSelected(%s)", item.getTitle());
        switch (item.getItemId()) {
            case R.id.navigation_menu_home:
                checkLocationPermission();
                break;
            case R.id.navigation_menu_friends:
                replaceFragment(FriendListFragment.newInstance(mUser.Id));
                break;
            case R.id.navigation_menu_preferences:
                mProgressBar.setIndeterminate(false);
                Fragment preferencesFragment = new UserPreferencesFragment();
                replaceFragment(preferencesFragment);
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
    public void onPause() {
        super.onPause();

        LogUtils.debug(TAG, "++onPause()");
        // TODO: might need to halt location timer during suspension to save battery; need testing
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        LogUtils.debug(TAG, "++onRequestPermissionResult(int, String[], int[])");
        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    LogUtils.debug(TAG, "ACCESS_FINE_LOCATION permission granted.");
                    queryUser();
                } else {
                    LogUtils.debug(TAG, "ACCESS_FINE_LOCATION permission denied; halting location task.");
                }

                break;
            case CONTACTS_PERMISSION_REQUEST_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    LogUtils.debug(TAG, "READ_CONTACTS permission granted.");
                    replaceFragment(ContactsFragment.newInstance());
                } else {
                    LogUtils.debug(TAG, "READ_CONTACTS permission denied; TBD");
                }

                break;
            default:
                LogUtils.debug(TAG, "Unknown request code: %d", requestCode);
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        LogUtils.debug(TAG, "++onResume()");
    }

    /*
        Event Handling
     */
    @Override
    public void onAcceptFriend(Friend friend) {

        LogUtils.debug(TAG, "++onAcceptFriend(Friend)");
        friend.Status = 2;
        String queryPath = PathUtils.combine(User.USERS_ROOT, mUser.Id, Friend.FRIENDS_ROOT);
        FirebaseFirestore.getInstance().collection(queryPath).document(friend.Id).set(friend)
            .addOnSuccessListener(aVoid -> LogUtils.debug(TAG, "Friend accept successfully written."))
            .addOnFailureListener(e -> LogUtils.warn(TAG, "Error writing friend accept: %s", e.getMessage()));

        /*
            TODO: replace with server side functions
            update mUser to friendId friend list too
         */
        queryPath = PathUtils.combine(User.USERS_ROOT, friend.Id, Friend.FRIENDS_ROOT);
        Friend userAsFriend = new Friend(mUser);
        friend.Status = 2;
        FirebaseFirestore.getInstance().collection(queryPath).document(mUser.Id).set(userAsFriend)
            .addOnSuccessListener(aVoid -> LogUtils.debug(TAG, "Friend list successfully written!"))
            .addOnFailureListener(e -> LogUtils.warn(TAG, "Error writing friend list: %s", e.getMessage()));
    }

    @Override
    public void onAddSharingContact(String name, String email) {

        LogUtils.debug(TAG, "++onAddSharingContact(String, String)");

        replaceFragment(MappingFragment.newInstance(mUser));

        // look for requested contact in data store
        Query userQuery = FirebaseFirestore.getInstance().collection(User.USERS_ROOT).whereEqualTo("Email", email);
        userQuery.get().addOnCompleteListener(userTask -> {

            if (userTask.isSuccessful() && userTask.getResult() != null && userTask.getResult().isEmpty()) {
                LogUtils.debug(TAG, "Contact not found, creating placeholder for %s", email);

                // add a place holder friend in list
                Friend friend = new Friend();
                friend.FullName = name;
                friend.Email = email;
                friend.Status = 1; // waiting
                String queryPath = PathUtils.combine(User.USERS_ROOT, mUser.Id, Friend.FRIENDS_ROOT);
                FirebaseFirestore.getInstance().collection(queryPath).document(friend.getEmailAsKey()).set(friend)
                    .addOnSuccessListener(aVoid -> LogUtils.debug(TAG, "Placeholder friend request successfully written for %s", friend.getEmailAsKey()))
                    .addOnFailureListener(e -> LogUtils.warn(TAG, "Error writing placeholder friend for %s - %s", friend.getEmailAsKey(), e.getMessage()));
            } else if (userTask.isSuccessful() && userTask.getResult() != null) {
                LogUtils.debug(TAG, "Contact found; creating request for %s", email);
                for (QueryDocumentSnapshot snapshot : userTask.getResult()) {
                    User contact = snapshot.toObject(User.class);
                    contact.Id = snapshot.getId();
                    Friend friend = new Friend(contact);
                    friend.Status = 1; // waiting
                    String queryPath = PathUtils.combine(User.USERS_ROOT, mUser.Id, Friend.FRIENDS_ROOT);
                    FirebaseFirestore.getInstance().collection(queryPath).document(friend.Id).set(friend)
                        .addOnSuccessListener(aVoid -> LogUtils.debug(TAG, "Request successfully written for %s", email))
                        .addOnFailureListener(e -> LogUtils.warn(TAG, "Error writing request for %s - %s", email, e.getMessage()));

                    // TODO: replace with server side function
                    // add request
                    friend = new Friend(mUser);
                    friend.Status = 0; // pending
                    queryPath = PathUtils.combine(User.USERS_ROOT, friend.Id, Friend.FRIENDS_ROOT);
                    FirebaseFirestore.getInstance().collection(queryPath).document(mUser.Id).set(friend)
                        .addOnSuccessListener(aVoid -> LogUtils.debug(TAG, "Request successfully written for %s", mUser.Email))
                        .addOnFailureListener(e -> LogUtils.warn(TAG, "Error writing request for %s - %s", mUser.Email, e.getMessage()));
                }
            } else if (userTask.isSuccessful()) {
                LogUtils.debug(TAG, "Task was successful, but results were empty.");
            } else {
                LogUtils.debug(TAG, "Task was unsuccessful.");
            }
        });
    }

    @Override
    public void onDeclineFriend(Friend friend) {

        LogUtils.debug(TAG, "++onDeclineFriend(Friend)");
        onDeleteFriend(friend);
    }

    @Override
    public void onDeleteFriend(Friend friend) {

        LogUtils.debug(TAG, "++onDeleteFriend(Friend)");

        // remove from friend list
        String queryPath = PathUtils.combine(User.USERS_ROOT, mUser.Id, Friend.FRIENDS_ROOT);
        FirebaseFirestore.getInstance().collection(queryPath).document(friend.Id).delete()
            .addOnSuccessListener(aVoid -> LogUtils.debug(TAG, "Successfully deleted document %s", friend.Id))
            .addOnFailureListener(e -> LogUtils.error(TAG, "Failed deleting document %s - %s", friend.Id, e.getMessage()));

        // TODO: replace with server side function
        // delete mUser from friendId friend list too
        queryPath = PathUtils.combine(User.USERS_ROOT, friend.Id, Friend.FRIENDS_ROOT);
        FirebaseFirestore.getInstance().collection(queryPath).document(mUser.Id).delete()
            .addOnSuccessListener(aVoid -> LogUtils.debug(TAG, "Successfully deleted document %s", mUser.Id))
            .addOnFailureListener(e -> LogUtils.error(TAG, "Failed deleting document %s - %s", mUser.Id, e.getMessage()));
    }

    @Override
    public void onDeleteRequest(Friend friend) {

        LogUtils.debug(TAG, "++onDeleteRequest(Friend)");
        onDeleteFriend(friend);
    }

    @Override
    public void onFriendListUpdated(Map<String, Friend> friendList) {

        LogUtils.debug(TAG, "++onFriendListUpdated(Map<String, Friend>)");
        //mFriendList = friendList;
    }

    @Override
    public void onNoFriends() {

        LogUtils.debug(TAG, "++onNoFriends()");
        Snackbar.make(findViewById(R.id.main_drawer_layout), getString(R.string.no_friends), Snackbar.LENGTH_LONG).show();
        mProgressBar.setIndeterminate(false);
    }

    @Override
    public void onPopulated(int size) {

        LogUtils.debug(TAG, "++onPopulated(int)");
        mProgressBar.setIndeterminate(false);
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

        replaceFragment(MappingFragment.newInstance(mUser));
    }

    @Override
    public void onSelected(String friendId) {

        LogUtils.debug(TAG, "++onSelected()");
        // TODO: replaceFragment(MappingFragment.newInstance(friendId)); <-- zoom to this user's location
    }

    @Override
    public void onShowContactList() {

        LogUtils.debug(TAG, "++onShowContactList()");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CONTACTS)) {
                LogUtils.debug(TAG, "Displaying contact permission rationale to provide additional context.");
                Snackbar.make(
                    findViewById(R.id.main_drawer_layout),
                    getString(R.string.permission_denied_explanation),
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(
                        getString(R.string.ok),
                        view -> ActivityCompat.requestPermissions(
                            MainActivity.this,
                            new String[]{Manifest.permission.READ_CONTACTS},
                            CONTACTS_PERMISSION_REQUEST_CODE))
                    .show();
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.READ_CONTACTS},
                    CONTACTS_PERMISSION_REQUEST_CODE);
            }
        } else {
            LogUtils.debug(TAG, "READ_CONTACTS permission granted.");
            replaceFragment(ContactsFragment.newInstance());
        }
    }

    /*
        Private Support Methods
     */
    private void checkLocationPermission() {

        LogUtils.debug(TAG, "++checkPermissions()");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                LogUtils.debug(TAG, "Displaying location permission rationale to provide additional context.");
                Snackbar.make(
                    findViewById(R.id.main_drawer_layout),
                    getString(R.string.permission_denied_explanation),
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(
                        getString(R.string.ok),
                        view -> ActivityCompat.requestPermissions(
                            MainActivity.this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            LOCATION_PERMISSION_REQUEST_CODE))
                    .show();
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            }
        } else {
            LogUtils.debug(TAG, "ACCESS_FINE_LOCATION permission granted.");
            queryUser();
        }
    }

    private void queryFriendListOfUsers(List<User> users) {

        LogUtils.debug(TAG, "++queryFriendListOfUsers(List<>)");
        for (User user : users) {
            String queryPath = PathUtils.combine(User.USERS_ROOT, user.Id, Friend.FRIENDS_ROOT);
            FirebaseFirestore.getInstance().collection(queryPath).get().addOnCompleteListener(task -> {

                if (task.isSuccessful()) {
                    if (task.getResult() != null) {
                        for (QueryDocumentSnapshot snapshot : task.getResult()) {

                            // grab the friends of user from data store
                            Friend friend = snapshot.toObject(Friend.class);
                            friend.Id = snapshot.getId();
                            if (friend.getEmailAsKey().equals(mUser.getEmailAsKey())) {

                                // copy existing friend and change path to Id instead of emailAsKey
                                Friend updatedFriend = new Friend(mUser);
                                updatedFriend.Status = 1; // waiting
                                String friendsPath = PathUtils.combine(User.USERS_ROOT, user.Id, Friend.FRIENDS_ROOT);
                                FirebaseFirestore.getInstance().collection(friendsPath).document(mUser.Id).set(updatedFriend)
                                    .addOnSuccessListener(aVoid -> {
                                        LogUtils.debug(TAG, "Friend created successfully.");

                                        // remove emailAsKey item
                                        String removePath = PathUtils.combine(User.USERS_ROOT, user.Id, Friend.FRIENDS_ROOT, mUser.getEmailAsKey());
                                        FirebaseFirestore.getInstance().document(removePath).delete();
                                    })
                                    .addOnFailureListener(e -> LogUtils.warn(TAG, "Error creating friend: %s", e.getMessage()));

                                // create a friend request for the current user
                                friendsPath = PathUtils.combine(User.USERS_ROOT, mUser.Id, Friend.FRIENDS_ROOT);
                                Friend requester = new Friend(user);
                                requester.Status = 0; // pending
                                FirebaseFirestore.getInstance().collection(friendsPath).document(requester.Id).set(requester)
                                    .addOnSuccessListener(aVoid -> LogUtils.debug(TAG, "Friend created successfully."))
                                    .addOnFailureListener(e -> LogUtils.warn(TAG, "Error creating friend: %s", e.getMessage()));
                            } else if (friend.Id.equals(mUser.Id)) {
                                // create a friend request for the current user
                                String friendsPath = PathUtils.combine(User.USERS_ROOT, mUser.Id, Friend.FRIENDS_ROOT);
                                Friend requester = new Friend(user);
                                requester.Status = 0; // pending
                                FirebaseFirestore.getInstance().collection(friendsPath).document(requester.Id).set(requester)
                                    .addOnSuccessListener(aVoid -> LogUtils.debug(TAG, "Friend created successfully."))
                                    .addOnFailureListener(e -> LogUtils.warn(TAG, "Error creating friend: %s", e.getMessage()));
                            }
                        }

                        mProgressBar.setIndeterminate(false);
                        replaceFragment(MappingFragment.newInstance(mUser));
                    } else {
                        LogUtils.debug(TAG, "Task result is null.");
                    }
                } else {
                    LogUtils.debug(TAG, "Task was unsuccessful.");
                }
            });
        }
    }

    private void queryUser() {

        LogUtils.debug(TAG, "++queryUser()");
        String userQueryPath = PathUtils.combine(User.USERS_ROOT, mUser.Id);
        FirebaseFirestore.getInstance().document(userQueryPath).get().addOnCompleteListener(task -> {

            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                User queriedUser = null;
                if (document != null) {
                    // found this user's data
                    queriedUser = document.toObject(User.class);
                    if (queriedUser != null) {
                        queriedUser.Id = document.getId();
                    }
                }

                if (queriedUser == null) {
                    LogUtils.debug(TAG, "User, %s, not found; creating.", mUser.Id);
                    queriedUser = new User();
                    queriedUser.Id = mUser.Id;
                    queriedUser.Email = mUser.Email;
                    queriedUser.FullName = mUser.FullName;
                    FirebaseFirestore.getInstance().collection(User.USERS_ROOT).document(mUser.Id).set(queriedUser)
                        .addOnSuccessListener(aVoid -> LogUtils.debug(TAG, "User created successfully."))
                        .addOnFailureListener(e -> LogUtils.warn(TAG, "Error creating user: %s", e.getMessage()));
                }

                // scan existing system user's friends list for current user's emailAsKey/friend requests
                updateFriendLists(queriedUser);
            } else {
                LogUtils.error(TAG, "get failed with ", task.getException());
            }
        });
    }

    private void replaceFragment(Fragment fragment) {

        LogUtils.debug(TAG, "++replaceFragment(Fragment)");
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.main_fragment_container, fragment);
        fragmentTransaction.commit();
        updateTitleAndDrawer(fragment);
    }

    /*
        TODO: replaced with server side functions
     */
    private void updateFriendLists(User searchForUser) {

        LogUtils.debug(TAG, "++updateFriendLists(User)");

        // get ready to move onto mapping fragment
        mUser = searchForUser;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.contains(UserPreferencesFragment.KEY_GET_FREQUENCY_SETTING)) {
            mUser.Frequency = Integer.parseInt(sharedPreferences.getString(UserPreferencesFragment.KEY_GET_FREQUENCY_SETTING, "-1"));
        }

        List<User> users = new ArrayList<>();
        FirebaseFirestore.getInstance().collection(User.USERS_ROOT).get().addOnCompleteListener(task -> {

            if (task.isSuccessful()) {
                if (task.getResult() != null) {

                    // look through all users of the system; ignoring the record for the current user
                    for (QueryDocumentSnapshot snapshot : task.getResult()) {
                        if (snapshot.getId().equals(searchForUser.Id)) {
                            continue;
                        }

                        users.add(snapshot.toObject(User.class));
                    }
                } else {
                    LogUtils.debug(TAG, "Task result is null.");
                }
            } else {
                LogUtils.debug(TAG, "Task was unsuccessful.");
            }

            queryFriendListOfUsers(users);
        });
    }

    private void updateTitleAndDrawer(Fragment fragment) {

        LogUtils.debug(TAG, "++updateTitleAndDrawer(Fragment)");
        String fragmentClassName = fragment.getClass().getName();
        if (fragmentClassName.equals(UserPreferencesFragment.class.getName())) {
            mNavigationView.getMenu().findItem(R.id.navigation_menu_home).setEnabled(true);
            mNavigationView.getMenu().findItem(R.id.navigation_menu_friends).setEnabled(true);
            mNavigationView.getMenu().findItem(R.id.navigation_menu_preferences).setEnabled(false);
            setTitle("Preferences");
        } else if (fragmentClassName.equals(FriendListFragment.class.getName())) {
            mNavigationView.getMenu().findItem(R.id.navigation_menu_home).setEnabled(true);
            mNavigationView.getMenu().findItem(R.id.navigation_menu_friends).setEnabled(false);
            mNavigationView.getMenu().findItem(R.id.navigation_menu_preferences).setEnabled(true);
            setTitle("Friends");
        } else if (fragmentClassName.equals(ContactsFragment.class.getName())) {
            mNavigationView.getMenu().findItem(R.id.navigation_menu_home).setEnabled(true);
            mNavigationView.getMenu().findItem(R.id.navigation_menu_friends).setEnabled(true);
            mNavigationView.getMenu().findItem(R.id.navigation_menu_preferences).setEnabled(true);
            setTitle("Select a contact");
        } else {
            mNavigationView.getMenu().findItem(R.id.navigation_menu_home).setEnabled(true);
            mNavigationView.getMenu().findItem(R.id.navigation_menu_friends).setEnabled(true);
            mNavigationView.getMenu().findItem(R.id.navigation_menu_preferences).setEnabled(true);
            setTitle(getString(R.string.app_name));
        }
    }
}
