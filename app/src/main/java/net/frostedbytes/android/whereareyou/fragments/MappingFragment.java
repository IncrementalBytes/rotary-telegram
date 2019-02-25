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

package net.frostedbytes.android.whereareyou.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.res.ResourcesCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import net.frostedbytes.android.whereareyou.BaseActivity;
import net.frostedbytes.android.whereareyou.R;
import net.frostedbytes.android.whereareyou.models.Friend;
import net.frostedbytes.android.whereareyou.models.User;
import net.frostedbytes.android.whereareyou.utils.DateUtils;
import net.frostedbytes.android.whereareyou.utils.LogUtils;
import net.frostedbytes.android.whereareyou.utils.PathUtils;

import static net.frostedbytes.android.whereareyou.BaseActivity.BASE_TAG;

public class MappingFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = BASE_TAG + MappingFragment.class.getSimpleName();

    public interface OnMappingListener {

        void onMapUpdated();
    }

    private OnMappingListener mCallback;

    private List<Friend> mFriendList;
    private double mPreviousLatitude;
    private double mPreviousLongitude;
    private User mUser;

    private final Handler mHandler = new Handler();
    private Timer mTimer;
    private TimerTask mTimerTask;
    private GoogleMap mGoogleMap;
    private MapView mMapView;
    private FusedLocationProviderClient mFusedLocationClient;

    public static MappingFragment newInstance(User user) {

        LogUtils.debug(TAG, "++newInstance(User)");
        MappingFragment fragment = new MappingFragment();
        Bundle args = new Bundle();
        args.putSerializable(BaseActivity.ARG_USER, user);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        LogUtils.debug(TAG, "++onAttach(Context)");
        try {
            mCallback = (OnMappingListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException("Not all callback methods have been implemented for " + context.toString());
        }

        Bundle arguments = getArguments();
        if (arguments != null) {
            mUser = (User)arguments.getSerializable(BaseActivity.ARG_USER);
        } else {
            LogUtils.error(TAG, "Arguments were null.");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        LogUtils.debug(TAG, "++onCreateView(LayoutInflater, ViewGroup, Bundle)");
        final View view = inflater.inflate(R.layout.fragment_mapping, container, false);

        mMapView = view.findViewById(R.id.mapping_map_view);
        mMapView.onCreate(savedInstanceState);
        mMapView.onResume();
        mMapView.getMapAsync(this);
        if (getActivity() != null) {
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(getActivity());
        }

        startLocationRetrievalTask();
        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();

        LogUtils.debug(TAG, "++onDestroy()");
        stopTimer();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();

        LogUtils.debug(TAG, "++onLowMemory()");
    }

    @Override
    public void onMapReady(GoogleMap map) {

        LogUtils.debug(TAG, "++onMapReady(GoogleMap)");
        mGoogleMap = map;
        UiSettings uiSettings = mGoogleMap.getUiSettings();
        uiSettings.setZoomControlsEnabled(true);
        uiSettings.setCompassEnabled(true);
        if (getActivity() != null &&
            ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mGoogleMap.setMyLocationEnabled(true);
            if (mUser != null && (mUser.Latitude > 0 && mUser.Longitude > 0)) {
                mGoogleMap.clear();
                LatLng position = new LatLng(mUser.Latitude, mUser.Longitude);
                mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15));
                mGoogleMap.animateCamera(CameraUpdateFactory.zoomIn());
                mGoogleMap.animateCamera(CameraUpdateFactory.zoomTo(12), 2000, null);
            }
        } else {
            LogUtils.warn(TAG, "User marker removed due to permissions.");
        }

        uiSettings.setScrollGesturesEnabled(true);
        uiSettings.setZoomGesturesEnabled(true);
        uiSettings.setRotateGesturesEnabled(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();

        LogUtils.debug(TAG, "++onResume()");
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mMapView.onSaveInstanceState(outState);

        LogUtils.debug(TAG, "++onSaveInstanceState(Bundle)");
    }

    /*
        Private Support Methods
     */
    @SuppressWarnings("MissingPermission")
    private void getLastLocation() {

        LogUtils.debug(TAG, "++getLastLocation()");
        if (mFusedLocationClient == null) {
            LogUtils.error(TAG, "Location client is not ready.");
        } else {
            mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        mUser.Latitude = location.getLatitude();
                        mUser.Longitude = location.getLongitude();
                    } else {
                        LogUtils.debug(TAG, "Location data appears to be cached; no movement by user; updating timestamp only.");
                    }

                    mUser.TimeStamp = Calendar.getInstance().getTimeInMillis();
                    LogUtils.debug(
                        TAG,
                        "Longitude: %f Latitude: %f Timestamp: %s",
                        mUser.Longitude,
                        mUser.Latitude,
                        DateUtils.formatDateForDisplay(mUser.TimeStamp));

                    // add/update location to firestore
                    FirebaseFirestore.getInstance().collection(User.USERS_ROOT).document(mUser.Id).set(mUser, SetOptions.merge())
                        .addOnSuccessListener(aVoid -> LogUtils.debug(TAG, "Location information successfully merged for %s", mUser.Id))
                        .addOnFailureListener(e -> LogUtils.warn(TAG, "Error merging location information for %s - %s", mUser.Id, e.getMessage()));
                    updateMap();

                    /*
                        TODO: replace with server side function
                     */
                    // update this user's location for all of this user's friends
                    if (mFriendList != null) {
                        Friend asFriend = new Friend(mUser);
                        asFriend.Longitude = mUser.Longitude;
                        asFriend.Latitude = mUser.Latitude;
                        asFriend.TimeStamp = mUser.TimeStamp;
                        for (Friend friend : mFriendList) {
                            if (friend.Status == 2) {
                                LogUtils.debug(TAG, "Attempting to update %s location under %s friend list.", mUser.Id, friend.Id);
                                String queryPath = PathUtils.combine(User.USERS_ROOT, friend.Id, Friend.FRIENDS_ROOT);
                                FirebaseFirestore.getInstance().collection(queryPath).document(mUser.Id).set(asFriend, SetOptions.merge())
                                    .addOnSuccessListener(aVoid -> LogUtils.debug(TAG, "Location information successfully merged for %s under %s", mUser.Id, friend.Id))
                                    .addOnFailureListener(e -> LogUtils.warn(TAG, "Error merging location information for %s under %s - %s", mUser.Id, friend.Id, e.getMessage()));
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> LogUtils.error(TAG, "Location client getLastLocation() failed for %s - %s", mUser.Id, e.getMessage()));
        }
    }

    private BitmapDescriptor getMarkerIconFromDrawable(Drawable drawable) {

        Canvas canvas = new Canvas();
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private void startLocationRetrievalTask() {

        LogUtils.debug(TAG, "++startLocationRetrievalTask()");
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
            LogUtils.warn(TAG, "User not initialized; waiting until next action to try again.");
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

    private void updateMap() {

        LogUtils.debug(TAG, "++updateMap()");

        if (mGoogleMap != null) {
            mGoogleMap.clear();

            // add user's location
            if (mPreviousLatitude == 0 && mPreviousLongitude == 0) {
                LatLng userPosition = new LatLng(mUser.Latitude, mUser.Longitude);
                mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userPosition, 15));
                mGoogleMap.animateCamera(CameraUpdateFactory.zoomIn());
                mGoogleMap.animateCamera(CameraUpdateFactory.zoomTo(12), 2000, null);
                mPreviousLatitude = mUser.Latitude;
                mPreviousLongitude = mUser.Longitude;
            }

            // setup listener on this user's friend path for updates
            String queryPath = PathUtils.combine(User.USERS_ROOT, mUser.Id, Friend.FRIENDS_ROOT);
            FirebaseFirestore.getInstance().collection(queryPath).whereEqualTo("Status", 2).get().addOnCompleteListener(task -> {

                if (task.isSuccessful() && task.getResult() != null) {
                    mFriendList = new ArrayList<>();
                    for (QueryDocumentSnapshot snapshot : task.getResult()) {
                        Friend friend = snapshot.toObject(Friend.class);
                        mFriendList.add(friend);
                        LatLng friendPosition = new LatLng(friend.Latitude, friend.Longitude);
                        Drawable markerDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_pin_light, null);
                        if (markerDrawable != null) {
                            BitmapDescriptor bitmap = getMarkerIconFromDrawable(markerDrawable);
                            MarkerOptions friendMarker = new MarkerOptions()
                                .position(friendPosition)
                                .title(friend.FullName)
                                .icon(bitmap);
                            mGoogleMap.addMarker(friendMarker);
                        } else {
                            LogUtils.debug(TAG, "Unable to create marker resource.");
                        }
                    }
                }

                mCallback.onMapUpdated();
            });
        }
    }
}
