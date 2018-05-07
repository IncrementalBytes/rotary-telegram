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
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
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
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.Calendar;
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

public class FriendsPageFragment extends Fragment {

  private static final String TAG = FriendsPageFragment.class.getSimpleName();

  private static final int REQUEST_CONTACT = 0;

  public interface OnFriendListListener {

    void onDeleteFriend(String userId, String friendId);
  }

  private OnFriendListListener mCallback;

  private RecyclerView mRecyclerView;

  private User mUser;

  public static FriendsPageFragment newInstance(User user) {

    LogUtils.debug(TAG, "++newInstance(User)");
    FriendsPageFragment fragment = new FriendsPageFragment();
    Bundle args = new Bundle();
    args.putSerializable(BaseActivity.ARG_USER, user);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    LogUtils.debug(TAG, "++onCreateView(LayoutInflater, ViewGroup, Bundle)");
    final View fragmentView = inflater.inflate(R.layout.fragment_friend_list, container, false);

    FloatingActionButton addUserButton = fragmentView.findViewById(R.id.friend_button_add);
    mRecyclerView = fragmentView.findViewById(R.id.friend_list_view);

    updateUI();

    final Intent pickContact = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
    addUserButton.setOnClickListener(view -> {

      LogUtils.debug(TAG, "++setOnClickListener()");
      startActivityForResult(pickContact, REQUEST_CONTACT);
    });

    return fragmentView;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);

    LogUtils.debug(TAG, "++onAttach(Context)");
    try {
      mCallback = (OnFriendListListener) context;
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

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {

    LogUtils.debug(TAG, "++onActivityResult(int, int, data)");
    if (requestCode == REQUEST_CONTACT) {
      if (getActivity() != null && getActivity().getContentResolver() != null) {
        String displayName;
        String email;
        try (Cursor cursor = getActivity().getContentResolver().query(
          Data.CONTENT_URI,
          new String[] { Data.CONTACT_ID, Data.MIMETYPE, Email.ADDRESS, Contacts.DISPLAY_NAME, Phone.NUMBER },
          null,
          null,
          Contacts.DISPLAY_NAME)) {
          if (cursor == null || cursor.getCount() == 0) {
            LogUtils.warn(TAG, "Contacts cursor is null or empty.");
            return;
          }

          cursor.moveToFirst();
          displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
          email = cursor.getString(cursor.getColumnIndex(Email.DATA));
          LogUtils.debug(TAG, "Name: %s", displayName);
          LogUtils.debug(TAG, "Email: %s", email);
        }

        // TODO: what to do with multiple email addresses?
        // add pending friend object to this users friend list
        if (!displayName.isEmpty() && !email.isEmpty()) {
          // make sure user isn't already a friend or pending invite already exists
          boolean existing = false;
          for (Friend friend : mUser.FriendList.values()) {
            if (friend.Email.equals(email)) {
              LogUtils.debug(TAG, "Already part of friends list.");
              existing = true;
              break;
            }
          }

          if (!existing) {
            String queryPath = PathUtils.combine(User.USERS_ROOT, mUser.UserId, User.FRIEND_LIST);

            // since we don't know the users firebase uid from the contact, we use the email and let the server functions do the update later
            Friend request = new Friend();
            request.Email = email;
            request.FullName = displayName;
            request.IsPending = true;
            request.CreatedDate = request.UpdatedDate = Calendar.getInstance().getTimeInMillis();
            FirebaseFirestore.getInstance().collection(queryPath).document(request.getEmailAsKey()).set(request);
          }
        } else {
          LogUtils.error(TAG, "Contact data is incomplete.");
        }
      }
    }
  }

  private void updateUI() {

    LogUtils.debug(TAG, "++updateUI()");
    if (mUser.FriendList.values().size() > 0) {
      List<Friend> friends = new ArrayList<>();
      for (Friend friend : mUser.FriendList.values()) {
        if (friend.IsAccepted) {
          friends.add(friend);
        }
      }

      FriendAdapter friendAdapter = new FriendAdapter(new ArrayList<>(friends));
      mRecyclerView.setAdapter(friendAdapter);
      mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
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

  class FriendHolder extends RecyclerView.ViewHolder {

    private final TextView mNameTextView;
    private final TextView mLastKnownDateTextView;
    private TouchableImageView mDeleteImageView;

    private Friend mFriend;

    FriendHolder(LayoutInflater inflater, ViewGroup parent) {
      super(inflater.inflate(R.layout.friend_item, parent, false));

      mNameTextView = itemView.findViewById(R.id.friend_item_name);
      mLastKnownDateTextView = itemView.findViewById(R.id.friend_item_last_timestamp);
      mDeleteImageView = itemView.findViewById(R.id.friend_item_delete);

      mDeleteImageView.setOnTouchListener((view, motionEvent) -> {

        switch (motionEvent.getAction()) {
          case MotionEvent.ACTION_DOWN:
            if (getActivity() != null) {
              AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(String.format(Locale.ENGLISH, "Remove %s from friend list?", mFriend.FullName))
                .setPositiveButton(android.R.string.ok, (positiveDialog, which) -> mCallback.onDeleteFriend(mUser.UserId, mFriend.UserId))
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
      if (friend.LocationList != null && friend.LocationList.size() > 0) {
        List<String> locationKeys = new ArrayList<>(friend.LocationList.keySet());
        Collections.sort(locationKeys);
        mLastKnownDateTextView.setText(
          String.format(
            Locale.ENGLISH,
            "%s %s",
            getString(R.string.last_active_header),
            DateUtils.formatDateForDisplay(Long.parseLong(locationKeys.remove(locationKeys.size() - 1)))));
      } else {
        mLastKnownDateTextView.setText(
          String.format(
            Locale.ENGLISH,
            "%s %s",
            getString(R.string.last_active_header),
            getString(R.string.not_available)));
      }
    }
  }
}
