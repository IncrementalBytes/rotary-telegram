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
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.List;
import net.frostedbytes.android.whereareyou.BaseActivity;
import net.frostedbytes.android.whereareyou.R;
import net.frostedbytes.android.whereareyou.models.UserLocation;
import net.frostedbytes.android.whereareyou.utils.DateUtils;
import net.frostedbytes.android.whereareyou.utils.LogUtils;
import net.frostedbytes.android.whereareyou.utils.PathUtils;

public class MappingFragment extends Fragment implements OnMapReadyCallback {

  private static final String TAG = MappingFragment.class.getSimpleName();

  private String mUserId;

  private GoogleMap mGoogleMap;
  private MapView mMapView;

  private ListenerRegistration mListenerRegistration;

  public static MappingFragment newInstance(String userId) {

    LogUtils.debug(TAG, "++newInstance(User)");
    MappingFragment fragment = new MappingFragment();
    Bundle args = new Bundle();
    args.putString(BaseActivity.ARG_USER_ID, userId);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    LogUtils.debug(TAG, "++onCreateView(LayoutInflater, ViewGroup, Bundle)");
    final View view = inflater.inflate(R.layout.fragment_mapping, container, false);

    mMapView = view.findViewById(R.id.mapping_map_view);
    mMapView.onCreate(savedInstanceState);
    mMapView.onResume();
    mMapView.getMapAsync(this);

    String queryPath = PathUtils.combine(UserLocation.LOCATIONS_ROOT, mUserId, UserLocation.LOCATION_LIST);
    Query query = FirebaseFirestore.getInstance().collection(queryPath);
    mListenerRegistration = query.addSnapshotListener((snapshot, e) -> {

      if (e != null) {
        LogUtils.error(TAG, "%s", e.getMessage());
        return;
      }

      if (snapshot == null) {
        LogUtils.error(TAG, "LocationList query snapshot was null.");
        return;
      }

      List<UserLocation> locations = new ArrayList<>();
      List<DocumentSnapshot> documents= snapshot.getDocuments();
      if (!documents.isEmpty()) {
        for (DocumentSnapshot document : documents) {
          UserLocation location = document.toObject(UserLocation.class);
          if (location != null) {
            location.TimeStamp = Long.parseLong(document.getId());
            locations.add(location);
          }
        }

        updateMap(locations);
      }
    });

    return view;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);

    LogUtils.debug(TAG, "++onAttach(Context)");
    Bundle arguments = getArguments();
    if (arguments != null) {
      mUserId = arguments.getString(BaseActivity.ARG_USER_ID);
    } else {
      LogUtils.error(TAG, "Arguments were null.");
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    mMapView.onDestroy();

    LogUtils.debug(TAG, "++onDestroy()");
    if (mListenerRegistration != null) {
      mListenerRegistration.remove();
      mListenerRegistration = null;
    }
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

    // Keep the UI Settings state in sync with the checkboxes.
    uiSettings.setZoomControlsEnabled(true);
    uiSettings.setCompassEnabled(true);
    if (getActivity() != null &&
      ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
      mGoogleMap.setMyLocationEnabled(true);
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

  private void updateMap(List<UserLocation> locations) {

    LogUtils.debug(TAG, "++updateMap(List<UserLocations>)");
    mGoogleMap.clear();
    for (int index = 0; index < locations.size(); index++) {
      LatLng position = new LatLng(locations.get(index).Latitude, locations.get(index).Longitude);
      Marker marker = mGoogleMap.addMarker(new MarkerOptions()
        .position(position)
        .title(DateUtils.formatDateForDisplay(locations.get(index).TimeStamp))
        .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_sentiment_neutral_black_24dp)));
      if ((index + 1) == locations.size()) {
        marker.showInfoWindow();
        marker.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.ic_sentiment_very_satisfied_black_24dp));
        mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15));
        mGoogleMap.animateCamera(CameraUpdateFactory.zoomIn());
        mGoogleMap.animateCamera(CameraUpdateFactory.zoomTo(15), 2000, null);
      }
    }
  }
}
