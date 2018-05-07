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

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.frostedbytes.android.whereareyou.BaseActivity;
import net.frostedbytes.android.whereareyou.R;
import net.frostedbytes.android.whereareyou.models.Friend;
import net.frostedbytes.android.whereareyou.models.User;
import net.frostedbytes.android.whereareyou.utils.DateUtils;
import net.frostedbytes.android.whereareyou.utils.LogUtils;
import net.frostedbytes.android.whereareyou.utils.PathUtils;
import net.frostedbytes.android.whereareyou.views.TouchableImageView;

public class MainListFragment extends Fragment {

  private static final String TAG = MainListFragment.class.getSimpleName();

  public interface OnMainListListener {

    void onNoFriends();
    void onPopulated(int size);
    void onSelected(String friendId);
  }

  private OnMainListListener mCallback;

  private RecyclerView mRecyclerView;

  private Query mQuery;
  private ListenerRegistration mListenerRegistration;

  private List<Friend> mFriends;
  private String mUserId;

  public static MainListFragment newInstance(String userId) {

    LogUtils.debug(TAG, "++newInstance(%s)", userId);
    MainListFragment fragment = new MainListFragment();
    Bundle args = new Bundle();
    args.putString(BaseActivity.ARG_USER_ID, userId);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    LogUtils.debug(TAG, "++onCreateView(LayoutInflater, ViewGroup, Bundle)");
    final View view = inflater.inflate(R.layout.fragment_main_list, container, false);

    mRecyclerView = view.findViewById(R.id.main_list_view);

    mFriends = null;

    String queryPath = PathUtils.combine(User.USERS_ROOT, mUserId, User.FRIEND_LIST);
    mQuery = FirebaseFirestore.getInstance().collection(queryPath);
    mListenerRegistration = mQuery.addSnapshotListener(new EventListener<QuerySnapshot>() {

      @Override
      public void onEvent(@Nullable QuerySnapshot snapshot, @Nullable FirebaseFirestoreException e) {

        if (e != null) {
          LogUtils.error(TAG, "%s", e.getMessage());
          return;
        }

        if (snapshot == null) {
          LogUtils.error(TAG, "FriendList query snapshot is null: %s", queryPath);
          return;
        }

        mFriends = new ArrayList<>();
        List<DocumentSnapshot> documents = snapshot.getDocuments();
        if (!documents.isEmpty()) {
          for (DocumentSnapshot document : documents) {
            Friend friend = document.toObject(Friend.class);
            if (friend != null) {
              friend.UserId = document.getId();
              mFriends.add(friend);
            }
          }
        } else {
          LogUtils.debug(TAG, "getDocuments() is empty: %s", queryPath);
        }

        updateUI();
      }
    });

    updateUI();

    return view;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);

    LogUtils.debug(TAG, "++onAttach(Context)");
    try {
      mCallback = (OnMainListListener) context;
    } catch (ClassCastException e) {
      throw new ClassCastException(
        String.format(Locale.ENGLISH, "%s must implement onNoFriends(), onPopulated(int), and onSelected(String).", context.toString()));
    }

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

    LogUtils.debug(TAG, "++onDestroy()");
    if (mListenerRegistration != null) {
      mListenerRegistration.remove();
      mListenerRegistration = null;
    }
  }

  private void updateUI() {

    LogUtils.debug(TAG, "++updateUI()");
    List<Friend> friends = new ArrayList<>();
    if (mFriends != null && !mFriends.isEmpty()) {
      // make sure user isn't pending; we only want to show them on the requests page until action is taken
      for (Friend friend : mFriends) {
        if (friend.IsAccepted) {
          friends.add(friend);
        }
      }
    }

    if (!friends.isEmpty()) {
      FriendAdapter friendAdapter = new FriendAdapter(new ArrayList<>(friends));
      mRecyclerView.setAdapter(friendAdapter);
      mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
      mCallback.onPopulated(friendAdapter.getItemCount()); // signal activity to dismiss progress dialog
    } else {
      mCallback.onNoFriends();
    }
  }

  class FriendAdapter extends RecyclerView.Adapter<FriendHolder> {

    private List<Friend> mFriends;

    FriendAdapter(List<Friend> friends) {

      mFriends = friends;
    }

    @NonNull
    @Override
    public FriendHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

      LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
      return new FriendHolder(layoutInflater, parent);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendHolder holder, int position) {

      Friend friend = mFriends.get(position);
      holder.bind(friend);
    }

    @Override
    public int getItemCount() {
      return mFriends.size();
    }
  }

  class FriendHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

    private final TextView mNameTextView;
    private final TextView mStatusTextView;
    private final TextView mLastKnownDateTextView;
    private final TouchableImageView mDeleteButton;

    private Friend mFriend;

    FriendHolder(LayoutInflater inflater, ViewGroup parent) {
      super(inflater.inflate(R.layout.friend_item, parent, false));

      itemView.setOnClickListener(this);
      mNameTextView = itemView.findViewById(R.id.friend_item_name);
      mStatusTextView = itemView.findViewById(R.id.friend_item_status);
      mLastKnownDateTextView = itemView.findViewById(R.id.friend_item_last_timestamp);
      mDeleteButton = itemView.findViewById(R.id.friend_item_delete);
    }

    void bind(Friend friend) {

      mFriend = friend;
      mDeleteButton.setVisibility(View.INVISIBLE);
      mNameTextView.setText(friend.FullName);
      if (friend.IsSharing) {
        mStatusTextView.setTextColor(Color.GREEN);
        mStatusTextView.setTypeface(null, Typeface.ITALIC);
      } else {
        mStatusTextView.setTextColor(Color.RED);
        mStatusTextView.setTypeface(null, Typeface.NORMAL);
      }

      mStatusTextView.setText(friend.IsSharing ? getString(R.string.status_sharing) : getString(R.string.status_not_sharing));
      if (friend.LocationList != null && friend.LocationList.size() > 0) {
        List<String> locationKeys = new ArrayList<>(friend.LocationList.keySet());
        Collections.sort(locationKeys);
        mLastKnownDateTextView.setText(
          String.format(
            Locale.ENGLISH,
            "%s %s",
            getString(R.string.timestamp_header),
            DateUtils.formatDateForDisplay(Long.parseLong(locationKeys.remove(locationKeys.size() - 1)))));
      } else {
        mLastKnownDateTextView.setText(getString(R.string.not_available));
      }
    }

    @Override
    public void onClick(View view) {

      LogUtils.debug(TAG, "%s clicked.", mFriend.FullName);
      mCallback.onSelected(mFriend.UserId);
    }
  }
}
