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
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

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

import static net.frostedbytes.android.whereareyou.BaseActivity.BASE_TAG;

public class FriendListFragment extends Fragment {

    private static final String TAG = BASE_TAG + FriendListFragment.class.getSimpleName();

    public interface OnFriendListListener {

        void onAcceptFriend(Friend friend);

        void onDeclineFriend(Friend friend);

        void onDeleteFriend(Friend friend);

        void onDeleteRequest(Friend friend);

        void onFriendListQueryComplete();

        void onListQueryFailed();

        void onSelected(String friendId);

        void onShowContactList();
    }

    private OnFriendListListener mCallback;

    private FloatingActionButton mAddFriendButton;
    private RecyclerView mRecyclerView;

    private ListenerRegistration mListenerRegistration;

    private Map<String, Friend> mFriendList;
    private User mUser;

    public static FriendListFragment newInstance(User user) {

        LogUtils.debug(TAG, "++newInstance(User)");
        FriendListFragment fragment = new FriendListFragment();
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
            mCallback = (OnFriendListListener) context;
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
        final View view = inflater.inflate(R.layout.fragment_main_list, container, false);

        mRecyclerView = view.findViewById(R.id.main_list_view);
        mAddFriendButton = view.findViewById(R.id.main_button_add_friend);

        // get list of friends for display
        String queryPath = PathUtils.combine(User.USERS_ROOT, mUser.Id, Friend.FRIENDS_ROOT);
        Query query = FirebaseFirestore.getInstance().collection(queryPath);
        mListenerRegistration = query.addSnapshotListener((snapshot, e) -> {

            if (e != null) {
                LogUtils.error(TAG, "%s", e.getMessage());
                mCallback.onListQueryFailed();
                return;
            }

            if (snapshot == null) {
                LogUtils.error(TAG, "FriendList query snapshot is null: %s", queryPath);
                mCallback.onListQueryFailed();
                return;
            }

            mFriendList = new HashMap<>();
            List<DocumentSnapshot> documents = snapshot.getDocuments();
            if (!documents.isEmpty()) {
                for (DocumentSnapshot document : documents) {
                    Friend friend = document.toObject(Friend.class);
                    if (friend != null) {
                        friend.Id = document.getId();
                        mFriendList.put(friend.Id, friend);
                    }
                }
            } else {
                LogUtils.debug(TAG, "getDocuments() is empty: %s", queryPath);
            }

            updateUI();
        });

        // look for any temporary friend requests based on email as key
        List<User> users = new ArrayList<>();
        FirebaseFirestore.getInstance().collection(User.USERS_ROOT).get().addOnCompleteListener(task -> {

            if (task.isSuccessful()) {
                if (task.getResult() != null) {

                    // look through all users of the system; ignoring the record for the current user
                    for (QueryDocumentSnapshot snapshot : task.getResult()) {
                        if (snapshot.getId().equals(mUser.Id)) {
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
            mCallback.onFriendListQueryComplete();
        });

        mAddFriendButton.setEnabled(false);
        mAddFriendButton.setOnClickListener(pickView -> mCallback.onShowContactList());

        return view;
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

    /*
        TODO: replace with server side function
     */
    private void queryFriendListOfUsers(List<User> appUsers) {

        LogUtils.debug(TAG, "++queryFriendListOfUsers(List<>)");
        for (User appUser : appUsers) {
            String queryPath = PathUtils.combine(User.USERS_ROOT, appUser.Id, Friend.FRIENDS_ROOT);
            FirebaseFirestore.getInstance().collection(queryPath).get().addOnCompleteListener(task -> {

                if (task.isSuccessful()) {
                    if (task.getResult() != null) {
                        for (QueryDocumentSnapshot snapshot : task.getResult()) {

                            // grab the friends of user from data store
                            Friend friend = snapshot.toObject(Friend.class);
                            friend.Id = snapshot.getId();
                            if (friend.Id.equals(mUser.getEmailAsKey())) {

                                // copy existing friend and change path to Id instead of emailAsKey
                                Friend updatedFriend = new Friend(mUser);
                                updatedFriend.Status = 1; // waiting
                                String friendsPath = PathUtils.combine(User.USERS_ROOT, appUser.Id, Friend.FRIENDS_ROOT);
                                FirebaseFirestore.getInstance().collection(friendsPath).document(mUser.Id).set(updatedFriend)
                                    .addOnSuccessListener(aVoid -> {
                                        LogUtils.debug(TAG, "Friend created successfully for %s under %s", mUser.Id, appUser.Id);

                                        // remove emailAsKey item
                                        String removePath = PathUtils.combine(User.USERS_ROOT, appUser.Id, Friend.FRIENDS_ROOT, mUser.getEmailAsKey());
                                        FirebaseFirestore.getInstance().document(removePath).delete()
                                            .addOnSuccessListener(aVoid1 -> LogUtils.debug(TAG, "Successfully removed old friend listing for %s under %s", mUser.getEmailAsKey(), appUser.Id))
                                            .addOnFailureListener(e -> LogUtils.debug(TAG, "Error removing old friend listing for %s under %s", mUser.getEmailAsKey(), appUser.Id));
                                    })
                                    .addOnFailureListener(e -> LogUtils.warn(TAG, "Error creating friend for %s under %s - %s", mUser.Id, appUser.Id, e.getMessage()));

                                // create a friend request for the current user
                                friendsPath = PathUtils.combine(User.USERS_ROOT, mUser.Id, Friend.FRIENDS_ROOT);
                                Friend requester = new Friend(appUser);
                                requester.Status = 0; // pending
                                FirebaseFirestore.getInstance().collection(friendsPath).document(requester.Id).set(requester)
                                    .addOnSuccessListener(aVoid -> LogUtils.debug(TAG, "Friend created successfully for %s under %s", appUser.Id, mUser.Id))
                                    .addOnFailureListener(e -> LogUtils.warn(TAG, "Error creating friend for %s under %s - %s", appUser.Id, mUser.Id, e.getMessage()));
                            } else if (friend.Id.equals(mUser.Id) && friend.Status == 1) {

                                // found waiting friend request under appUser, need to create a pending friend request under the current user
                                String friendsPath = PathUtils.combine(User.USERS_ROOT, mUser.Id, Friend.FRIENDS_ROOT);
                                Friend requester = new Friend(appUser);
                                requester.Status = 0; // pending
                                FirebaseFirestore.getInstance().collection(friendsPath).document(requester.Id).set(requester)
                                    .addOnSuccessListener(aVoid -> LogUtils.debug(TAG, "Friend request created successfully for %s under %s", requester.Id, mUser.Id))
                                    .addOnFailureListener(e -> LogUtils.warn(TAG, "Error creating friend request for %s under %s - %s", requester.Id, mUser.Id, e.getMessage()));
                            }
                        }
                    } else {
                        LogUtils.debug(TAG, "Task result is null.");
                    }
                } else {
                    LogUtils.debug(TAG, "Task was unsuccessful.");
                }
            });
        }
    }

    private void updateUI() {

        LogUtils.debug(TAG, "++updateUI()");
        mAddFriendButton.setEnabled(true);
        FriendAdapter friendAdapter = new FriendAdapter(new ArrayList<>(mFriendList.values()));
        mRecyclerView.setAdapter(friendAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        if (mFriendList.isEmpty()) {
            LogUtils.debug(TAG, "No friends were found for user.");
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
        private final TextView mLastKnownDateTextView;
        private final Switch mVisibleSwitch;
        private final TouchableImageView mAcceptImageView;
        private final TouchableImageView mDeclineImageView;
        private final TouchableImageView mDeleteImageView;

        private Friend mFriend;

        FriendHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.friend_item, parent, false));

            itemView.setOnClickListener(this);
            mNameTextView = itemView.findViewById(R.id.friend_item_name);
            mLastKnownDateTextView = itemView.findViewById(R.id.friend_item_last_timestamp);
            mVisibleSwitch = itemView.findViewById(R.id.friend_item_visible);
            mAcceptImageView = itemView.findViewById(R.id.friend_item_accept);
            mDeclineImageView = itemView.findViewById(R.id.friend_item_decline);
            mDeleteImageView = itemView.findViewById(R.id.friend_item_delete);

            mAcceptImageView.setOnTouchListener((view, motionEvent) -> {

                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mCallback.onAcceptFriend(mFriend);
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
                                .setPositiveButton(android.R.string.ok, (positiveDialog, which) -> mCallback.onDeclineFriend(mFriend))
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
                                .setTitle(String.format(Locale.ENGLISH, mFriend.Status == 0 ? "Delete request for %s?" : "Delete sharing with %s?", mFriend.FullName))
                                .setPositiveButton(android.R.string.ok, (DialogInterface positiveDialog, int which) -> {
                                    if (mFriend.Status == 0) {
                                        mCallback.onDeleteRequest(mFriend);
                                    } else {
                                        mCallback.onDeleteFriend(mFriend);
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

            mNameTextView.setText(friend.FullName);
            mDeleteImageView.setVisibility(View.VISIBLE);
            switch (mFriend.Status) {
                case 0:
                    mVisibleSwitch.setChecked(false);
                    mVisibleSwitch.setEnabled(false);
                    mAcceptImageView.setVisibility(View.VISIBLE);
                    mDeclineImageView.setVisibility(View.VISIBLE);
                    mLastKnownDateTextView.setText(getString(R.string.request_pending));
                    break;
                case 1:
                    mVisibleSwitch.setChecked(false);
                    mVisibleSwitch.setEnabled(false);
                    mAcceptImageView.setVisibility(View.INVISIBLE);
                    mDeclineImageView.setVisibility(View.INVISIBLE);
                    mLastKnownDateTextView.setText(getString(R.string.request_sent));
                    break;
                case 2:
                    mVisibleSwitch.setEnabled(true);
                    mAcceptImageView.setVisibility(View.INVISIBLE);
                    mDeclineImageView.setVisibility(View.INVISIBLE);
                    mLastKnownDateTextView.setText(DateUtils.formatDateForDisplay(friend.UpdatedDate));
                    break;
                case 3:
                    mVisibleSwitch.setChecked(false);
                    mVisibleSwitch.setEnabled(false);
                    mAcceptImageView.setVisibility(View.INVISIBLE);
                    mDeclineImageView.setVisibility(View.INVISIBLE);
                    mLastKnownDateTextView.setText(getString(R.string.request_declined));
            }
        }

        @Override
        public void onClick(View view) {

            LogUtils.debug(TAG, "%s clicked.", mFriend.FullName);
            if (mFriend.Status == 1) {
                mCallback.onSelected(mFriend.Id);
            }
        }
    }
}
