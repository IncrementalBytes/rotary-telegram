package net.frostedbytes.android.whereareyou.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;
import net.frostedbytes.android.whereareyou.BaseActivity;
import net.frostedbytes.android.whereareyou.R;
import net.frostedbytes.android.whereareyou.models.UserLocation;
import net.frostedbytes.android.whereareyou.utils.DateUtils;
import net.frostedbytes.android.whereareyou.utils.LogUtils;

public class MappingFragment extends Fragment implements OnMapReadyCallback {

  private static final String TAG = MappingFragment.class.getSimpleName();

  private String mFriendId;

  private MapView mMapView;
  private GoogleMap mGoogleMap;

  private Query mUserLocationQuery;
  private ValueEventListener mUserLocationValueListener;

  public static MappingFragment newInstance(String friendId) {

    LogUtils.debug(TAG, "++newInstance(String)");
    MappingFragment fragment = new MappingFragment();
    Bundle args = new Bundle();
    args.putString(BaseActivity.ARG_FRIEND_ID, friendId);
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

    mUserLocationQuery = FirebaseDatabase.getInstance().getReference().child(UserLocation.ROOT).child(mFriendId).orderByChild("TimeStamp");
    mUserLocationValueListener = new ValueEventListener() {

      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {

        List<UserLocation> locations = new ArrayList<>();
        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
          UserLocation location = snapshot.getValue(UserLocation.class);
          locations.add(location);
        }

        updateMap(locations);
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {

        LogUtils.debug(TAG, "++onCancelled(DatabaseError)");
        LogUtils.error(TAG, databaseError.getMessage());
      }
    };
    mUserLocationQuery.addValueEventListener(mUserLocationValueListener);

    return view;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);

    LogUtils.debug(TAG, "++onAttach(Context)");
    Bundle arguments = getArguments();
    if (arguments != null) {
      mFriendId = arguments.getString(BaseActivity.ARG_FRIEND_ID);
    } else {
      LogUtils.error(TAG, "Arguments were null.");
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    mMapView.onDestroy();

    LogUtils.debug(TAG, "++onDestroy()");
    if (mUserLocationQuery != null && mUserLocationValueListener != null) {
      mUserLocationQuery.removeEventListener(mUserLocationValueListener);
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

    for (UserLocation location : locations) {
      LatLng marker = new LatLng(location.Latitude, location.Longitude);
      mGoogleMap.addMarker(new MarkerOptions().position(marker).title(DateUtils.formatDateForDisplay(location.TimeStamp)));
      mGoogleMap.moveCamera(CameraUpdateFactory.newLatLng(marker));
    }
  }
}
