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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

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
    MappingFragment.OnMappingListener,
    UserPreferencesFragment.OnPreferencesListener {

    private static final String TAG = BASE_TAG + MainActivity.class.getSimpleName();

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 34;
    private static final int CONTACTS_PERMISSION_REQUEST_CODE = 35;

    private DrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;
    private ProgressBar mProgressBar;

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
        checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, LOCATION_PERMISSION_REQUEST_CODE);
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
                replaceFragment(MappingFragment.newInstance(mUser));
            }
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        LogUtils.debug(TAG, "++onNavigationItemSelected(%s)", item.getTitle());
        switch (item.getItemId()) {
            case R.id.navigation_menu_home:
                checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, LOCATION_PERMISSION_REQUEST_CODE);
                break;
            case R.id.navigation_menu_friends:
                replaceFragment(FriendListFragment.newInstance(mUser));
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        LogUtils.debug(TAG, "++onRequestPermissionResult(int, String[], int[])");
        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    LogUtils.debug(TAG, "ACCESS_FINE_LOCATION permission granted.");
                    replaceFragment(MappingFragment.newInstance(mUser));
                } else {
                    LogUtils.debug(TAG, "ACCESS_FINE_LOCATION permission denied.");
                }

                break;
            case CONTACTS_PERMISSION_REQUEST_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    LogUtils.debug(TAG, "READ_CONTACTS permission granted.");
                    replaceFragment(ContactsFragment.newInstance());
                } else {
                    LogUtils.debug(TAG, "READ_CONTACTS permission denied.");
                }

                break;
            default:
                LogUtils.debug(TAG, "Unknown request code: %d", requestCode);
                break;
        }
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
        userAsFriend.Status = 2;
        FirebaseFirestore.getInstance().collection(queryPath).document(mUser.Id).set(userAsFriend)
            .addOnSuccessListener(aVoid -> LogUtils.debug(TAG, "Friend list successfully written!"))
            .addOnFailureListener(e -> LogUtils.warn(TAG, "Error writing friend list: %s", e.getMessage()));
    }

    @Override
    public void onAddSharingContact(String name, String email) {

        LogUtils.debug(TAG, "++onAddSharingContact(String, String)");
        replaceFragment(MappingFragment.newInstance(mUser));

        // look for requested contact in data store
        FirebaseFirestore.getInstance().collection(User.USERS_ROOT).whereEqualTo("Email", email).get().addOnCompleteListener(userTask -> {

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

                    /*
                        TODO: replace with server side functions
                        add request
                     */
                    friend = new Friend();
                    friend.Id = mUser.Id;
                    friend.Email = mUser.Email;
                    friend.FullName = mUser.Email;
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

        /*
            TODO: replace with server side functions
            delete mUser from friendId friend list too
         */
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
    public void onFriendListQueryComplete() {

        LogUtils.debug(TAG, "++onFriendListQueryComplete()");
        mProgressBar.setIndeterminate(false);
    }

    @Override
    public void onListQueryFailed() {

        LogUtils.debug(TAG, "++onListQueryFailed()");
        mProgressBar.setIndeterminate(false);
        Snackbar.make(
            findViewById(R.id.main_drawer_layout),
            getString(R.string.error_friend_list_query),
            Snackbar.LENGTH_INDEFINITE)
            .show();
    }

    @Override
    public void onMapUpdated() {

        LogUtils.debug(TAG, "++onMapUpdated()");
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
        } else {
            LogUtils.warn(TAG, "User was not initialized; defaulting frequency value.");
        }

        replaceFragment(MappingFragment.newInstance(mUser));
    }

    @Override
    public void onSelected(String friendId) {

        LogUtils.debug(TAG, "++onSelected()");
        // TODO: replaceFragment(MappingFragment.newInstance(friendId)); <-- zoom to this user's location
    }

    public void onShowContactList() {

        LogUtils.debug(TAG, "++onShowContactList()");
        checkPermission(Manifest.permission.READ_CONTACTS, CONTACTS_PERMISSION_REQUEST_CODE);
    }

    /*
        Private Support Methods
     */
    private void checkPermission(String permission, int permissionCode) {

        LogUtils.debug(TAG, "++checkPermission()");
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                LogUtils.debug(TAG, "Displaying permission rationale to provide additional context.");
                Snackbar.make(
                    findViewById(R.id.main_drawer_layout),
                    getString(R.string.permission_denied_explanation),
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(
                        getString(R.string.ok),
                        view -> ActivityCompat.requestPermissions(
                            MainActivity.this,
                            new String[]{permission},
                            permissionCode))
                    .show();
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    new String[]{permission},
                    permissionCode);
            }
        } else {
            LogUtils.debug(TAG, "%s permission granted.", permission);
            switch (permissionCode) {
                case CONTACTS_PERMISSION_REQUEST_CODE:
                    replaceFragment(ContactsFragment.newInstance());
                    break;
                case LOCATION_PERMISSION_REQUEST_CODE:
                    replaceFragment(MappingFragment.newInstance(mUser));
                    break;
            }
        }
    }

    private void replaceFragment(Fragment fragment) {

        LogUtils.debug(TAG, "++replaceFragment(Fragment)");
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.main_fragment_container, fragment);
        fragmentTransaction.commit();
        updateTitleAndDrawer(fragment);
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
