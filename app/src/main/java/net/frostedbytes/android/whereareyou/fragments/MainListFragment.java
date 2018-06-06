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
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    void onAcceptFriend(String friendId);
    void onDeclineFriend(String friendId);
    void onDeleteFriend(String friendId);
    void onDeleteRequest(String friendId);
    void onFriendListUpdated(Map<String, Friend> friendList);
    void onNoFriends();
    void onPopulated(int size);
    void onSelected(String friendId);
    void onShowContactList();
  }

  private OnMainListListener mCallback;

  private FloatingActionButton mAddFriendButton;
  private RecyclerView mRecyclerView;

  private ListenerRegistration mListenerRegistration;

  private Map<String, Friend> mFriendList;
  private User mUser;

  public static MainListFragment newInstance(User user) {

    LogUtils.debug(TAG, "++newInstance(%s)", user.UserId);
    MainListFragment fragment = new MainListFragment();
    Bundle args = new Bundle();
    args.putSerializable(BaseActivity.ARG_USER, user);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    LogUtils.debug(TAG, "++onCreateView(LayoutInflater, ViewGroup, Bundle)");
    final View view = inflater.inflate(R.layout.fragment_main_list, container, false);

    mRecyclerView = view.findViewById(R.id.main_list_view);
    mAddFriendButton = view.findViewById(R.id.main_button_add_friend);

    String queryPath = PathUtils.combine(Friend.FRIENDS_ROOT, mUser.UserId, Friend.FRIEND_LIST);
    Query query = FirebaseFirestore.getInstance().collection(queryPath);
    mListenerRegistration = query.addSnapshotListener((snapshot, e) -> {

      if (e != null) {
        LogUtils.error(TAG, "%s", e.getMessage());
        return;
      }

      if (snapshot == null) {
        LogUtils.error(TAG, "FriendList query snapshot is null: %s", queryPath);
        return;
      }

      mFriendList = new HashMap<>();
      List<DocumentSnapshot> documents = snapshot.getDocuments();
      if (!documents.isEmpty()) {
        for (DocumentSnapshot document : documents) {
          Friend friend = document.toObject(Friend.class);
          if (friend != null) {
            friend.UserId = document.getId();
            mFriendList.put(friend.UserId, friend);
          }
        }

        mCallback.onFriendListUpdated(mFriendList);
      } else {
        LogUtils.debug(TAG, "getDocuments() is empty: %s", queryPath);
      }

      updateUI();
    });

    mAddFriendButton.setEnabled(false);
    mAddFriendButton.setOnClickListener(pickView -> mCallback.onShowContactList());

    return view;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);

    LogUtils.debug(TAG, "++onAttach(Context)");
    try {
      mCallback = (OnMainListListener) context;
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
    mAddFriendButton.setEnabled(true);
    FriendAdapter friendAdapter = new FriendAdapter(new ArrayList<>(mFriendList.values()));
    mRecyclerView.setAdapter(friendAdapter);
    mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
    if (!mFriendList.isEmpty()) {
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
    private final TouchableImageView mAcceptImageView;
    private final TouchableImageView mDeclineImageView;
    private final TouchableImageView mDeleteImageView;

    private Friend mFriend;

    FriendHolder(LayoutInflater inflater, ViewGroup parent) {
      super(inflater.inflate(R.layout.friend_item, parent, false));

      itemView.setOnClickListener(this);
      mNameTextView = itemView.findViewById(R.id.friend_item_name);
      mStatusTextView = itemView.findViewById(R.id.friend_item_status);
      mLastKnownDateTextView = itemView.findViewById(R.id.friend_item_last_timestamp);
      mAcceptImageView = itemView.findViewById(R.id.friend_item_accept);
      mDeclineImageView = itemView.findViewById(R.id.friend_item_decline);
      mDeleteImageView = itemView.findViewById(R.id.friend_item_delete);

      mAcceptImageView.setOnTouchListener((view, motionEvent) -> {

        switch (motionEvent.getAction()) {
          case MotionEvent.ACTION_DOWN:
            mCallback.onAcceptFriend(mFriend.UserId);
            break;
          case MotionEvent.ACTION_UP:
            view.performClick();
            return true;
        }

        return true;
      });

      mDeclineImageView.setOnTouchListener((view, motionEvent) -> {

        switch (motionEvent.getAction()) {
          case MotionEvent.ACTION_DOWN:
            if (getActivity() != null) {
              AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(String.format(Locale.ENGLISH, "Decline friend request from %s?", mFriend.FullName))
                .setPositiveButton(android.R.string.ok, (positiveDialog, which) -> mCallback.onDeclineFriend(mFriend.UserId))
                .setNegativeButton(android.R.string.cancel, (negativeDialog, which) -> {
                })
                .create();
              dialog.show();
            } else {
              LogUtils.error(TAG, "Activity is null in view holder.");
            }
            break;
          case MotionEvent.ACTION_UP:
            view.performClick();
            return true;
        }

        return true;
      });

      mDeleteImageView.setOnTouchListener((View view, MotionEvent motionEvent) -> {

        switch (motionEvent.getAction()) {
          case MotionEvent.ACTION_DOWN:
            if (getActivity() != null) {
              AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(String.format(Locale.ENGLISH, mFriend.IsPending ? "Delete request for %s?" : "Delete sharing with %s?", mFriend.FullName))
                .setPositiveButton(android.R.string.ok, (DialogInterface positiveDialog, int which) -> {
                  if (mFriend.IsPending) {
                    mCallback.onDeleteRequest(mFriend.UserId);
                  } else {
                    mCallback.onDeleteFriend(mFriend.UserId);
                  }
                })
                .setNegativeButton(android.R.string.cancel, (negativeDialog, which) -> {
                })
                .create();
              dialog.show();
            } else {
              LogUtils.error(TAG, "Activity is null in view holder.");
            }
            break;
          case MotionEvent.ACTION_UP:
            view.performClick();
            return true;
        }

        return true;
      });
    }

    void bind(Friend friend) {

      mFriend = friend;
      mAcceptImageView.setVisibility(View.INVISIBLE);
      mDeclineImageView.setVisibility(View.INVISIBLE);
      if (mFriend.IsPending && !mFriend.IsRequestedBy) {
        mAcceptImageView.setVisibility(View.VISIBLE);
        mDeclineImageView.setVisibility(View.VISIBLE);
      }

      mDeleteImageView.setVisibility(View.VISIBLE);

      mNameTextView.setText(friend.FullName);
      if (mFriend.IsSharing) {
        mStatusTextView.setTextColor(Color.GREEN);
        mStatusTextView.setTypeface(null, Typeface.ITALIC);
      } else {
        mStatusTextView.setTextColor(Color.RED);
        mStatusTextView.setTypeface(null, Typeface.NORMAL);
      }

      mLastKnownDateTextView.setText(DateUtils.formatDateForDisplay(friend.UpdatedDate));
      if (mFriend.IsPending && !mFriend.IsRequestedBy) {
        mStatusTextView.setText(getString(R.string.request_sent_date_header));
      } else if (mFriend.IsPending && mFriend.IsRequestedBy) {
        mStatusTextView.setText(getString(R.string.request_pending_date_header));
      } else {
        mStatusTextView.setText(friend.IsSharing ? getString(R.string.status_sharing) : getString(R.string.status_not_sharing));
      }
    }

    @Override
    public void onClick(View view) {

      LogUtils.debug(TAG, "%s clicked.", mFriend.FullName);
      if (mFriend.IsAccepted) {
        mCallback.onSelected(mFriend.UserId);
      }
    }
  }
}
