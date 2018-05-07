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
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.frostedbytes.android.whereareyou.BaseActivity;
import net.frostedbytes.android.whereareyou.R;
import net.frostedbytes.android.whereareyou.models.Friend;
import net.frostedbytes.android.whereareyou.models.User;
import net.frostedbytes.android.whereareyou.utils.DateUtils;
import net.frostedbytes.android.whereareyou.utils.LogUtils;
import net.frostedbytes.android.whereareyou.views.TouchableImageView;

public class RequestsPageFragment extends Fragment {

  private static final String TAG = RequestsPageFragment.class.getSimpleName();

  public interface OnRequestListListener {

    void onAcceptFriend(String userId, String friendId);
    void onDeclineFriend(String userId, String friendId);
    void onDeleteRequest(String userId, String friendId);
  }

  private OnRequestListListener mCallback;

  private RecyclerView mRecyclerView;

  private User mUser;

  public static RequestsPageFragment newInstance(User user) {

    LogUtils.debug(TAG, "++newInstance(User)");
    RequestsPageFragment fragment = new RequestsPageFragment();
    Bundle args = new Bundle();
    args.putSerializable(BaseActivity.ARG_USER, user);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    LogUtils.debug(TAG, "++onCreateView(LayoutInflater, ViewGroup, Bundle)");
    final View fragmentView = inflater.inflate(R.layout.fragment_request_list, container, false);

    mRecyclerView = fragmentView.findViewById(R.id.request_list_view);

    updateUI();

    return fragmentView;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);

    LogUtils.debug(TAG, "++onAttach(Context)");
    try {
      mCallback = (OnRequestListListener) context;
    } catch (ClassCastException e) {
      throw new ClassCastException(context.toString() + " must implement TBD.");
    }

    Bundle arguments = getArguments();
    if (arguments != null) {
      mUser = (User)arguments.getSerializable(BaseActivity.ARG_USER);
    } else {
      LogUtils.error(TAG, "Arguments were null.");
    }
  }

  private void updateUI() {

    LogUtils.debug(TAG, "++updateUI()");
    if (mUser.FriendList.values().size() > 0) {
      List<Friend> friends = new ArrayList<>();
      for (Friend friend : mUser.FriendList.values()) {
        if (friend.IsPending) {
          friends.add(friend);
        }
      }

      RequestAdapter requestAdapter = new RequestAdapter(new ArrayList<>(friends));
      mRecyclerView.setAdapter(requestAdapter);
      mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
    }
  }

  class RequestAdapter extends RecyclerView.Adapter<RequestHolder> {

    private List<Friend> mFriends;

    RequestAdapter(List<Friend> friends) {

      mFriends = friends;
    }

    @NonNull
    @Override
    public RequestHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

      LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
      return new RequestHolder(layoutInflater, parent);
    }

    @Override
    public void onBindViewHolder(@NonNull RequestHolder holder, int position) {

      holder.bind(mFriends.get(position));
    }

    @Override
    public int getItemCount() {
      return mFriends.size();
    }
  }

  class RequestHolder extends RecyclerView.ViewHolder {

    private final TextView mNameTextView;
    private final TextView mRequestDateTextView;
    private TouchableImageView mAcceptImageView;
    private TouchableImageView mDeclineImageView;
    private TouchableImageView mDeleteImageView;

    private Friend mFriend;

    RequestHolder(LayoutInflater inflater, ViewGroup parent) {
      super(inflater.inflate(R.layout.request_item, parent, false));

      mNameTextView = itemView.findViewById(R.id.request_item_name);
      mRequestDateTextView = itemView.findViewById(R.id.request_item_timestamp);
      mAcceptImageView = itemView.findViewById(R.id.request_item_accept);
      mDeclineImageView = itemView.findViewById(R.id.request_item_decline);
      mDeleteImageView = itemView.findViewById(R.id.request_item_delete);

      mAcceptImageView.setOnTouchListener((view, motionEvent) -> {

        switch (motionEvent.getAction()) {
          case MotionEvent.ACTION_DOWN:
            mCallback.onAcceptFriend(mUser.UserId, mFriend.UserId);
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
                .setPositiveButton(android.R.string.ok, (positiveDialog, which) -> mCallback.onDeclineFriend(mUser.UserId, mFriend.UserId))
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

      mDeleteImageView.setOnTouchListener((view, motionEvent) -> {

        switch (motionEvent.getAction()) {
          case MotionEvent.ACTION_DOWN:
            if (getActivity() != null) {
              AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(String.format(Locale.ENGLISH, "Delete request for %s?", mFriend.FullName))
                .setPositiveButton(android.R.string.ok, (positiveDialog, which) -> mCallback.onDeleteRequest(mUser.UserId, mFriend.UserId))
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
      mNameTextView.setText(friend.FullName);
      if (mFriend.IsPending) {
        mAcceptImageView.setVisibility(View.INVISIBLE);
        mDeleteImageView.setVisibility(View.VISIBLE);
        mDeclineImageView.setVisibility(View.INVISIBLE);
      } else {
        mAcceptImageView.setVisibility(View.VISIBLE);
        mDeleteImageView.setVisibility(View.INVISIBLE);
        mDeclineImageView.setVisibility(View.VISIBLE);
      }

      mRequestDateTextView.setText(
        String.format(
          Locale.ENGLISH,
          "%s %s",
          getString(R.string.request_date_header),
          DateUtils.formatDateForDisplay(friend.UpdatedDate)));
    }
  }
}
