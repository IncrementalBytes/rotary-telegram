package net.frostedbytes.android.whereareyou.fragments;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.frostedbytes.android.whereareyou.BaseActivity;
import net.frostedbytes.android.whereareyou.R;
import net.frostedbytes.android.whereareyou.models.User;
import net.frostedbytes.android.whereareyou.utils.DateUtils;
import net.frostedbytes.android.whereareyou.utils.LogUtils;

public class FriendListFragment extends Fragment {

  private static final String TAG = FriendListFragment.class.getSimpleName();

  public interface OnFriendListListener {

    void onNoFriends();

    void onPopulated(int size);

    void onSelected(String friendId);
  }

  private OnFriendListListener mCallback;

  private RecyclerView mRecyclerView;

  private List<User> mFriends;
  private String mUserId;

  private Query mFriendsQuery;
  private ValueEventListener mFriendsValueListener;

  public static FriendListFragment newInstance(String userId) {

    LogUtils.debug(TAG, "++newInstance(User)");
    FriendListFragment fragment = new FriendListFragment();
    Bundle args = new Bundle();
    args.putString(BaseActivity.ARG_USER_ID, userId);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    LogUtils.debug(TAG, "++onCreateView(LayoutInflater, ViewGroup, Bundle)");
    final View view = inflater.inflate(R.layout.fragment_friend_list, container, false);

    mRecyclerView = view.findViewById(R.id.friend_list_view);

    mFriends = null;
    String queryPath = User.USERS_ROOT + "/" + mUserId + "/" + User.USER_FRIENDS_ROOT;
    LogUtils.debug(TAG, "QueryPath: %s", queryPath);
    mFriendsQuery = FirebaseDatabase.getInstance().getReference().child(queryPath);
    mFriendsValueListener = new ValueEventListener() {

      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {

        mFriends = new ArrayList<>();
        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
          User friend = snapshot.getValue(User.class);
          if (friend != null) {
            friend.UserId = snapshot.getKey();
            mFriends.add(friend);
          }
        }

        updateUI();
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {

        LogUtils.debug(TAG, "++onCancelled(DatabaseError)");
        LogUtils.error(TAG, databaseError.getMessage());
      }
    };
    mFriendsQuery.addValueEventListener(mFriendsValueListener);

    updateUI();

    return view;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);

    LogUtils.debug(TAG, "++onAttach(Context)");
    try {
      mCallback = (OnFriendListListener) context;
    } catch (ClassCastException e) {
      throw new ClassCastException(context.toString() + " must implement onNoFriends(), onPopulated(int), and onSelected(String).");
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
    if (mFriendsQuery != null && mFriendsValueListener != null) {
      mFriendsQuery.removeEventListener(mFriendsValueListener);
    }
  }

  private void updateUI() {

    LogUtils.debug(TAG, "++updateUI()");
    if (mFriends != null && !mFriends.isEmpty()) {
      FriendAdapter friendAdapter = new FriendAdapter(new ArrayList<>(mFriends));
      mRecyclerView.setAdapter(friendAdapter);
      mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
      mCallback.onPopulated(friendAdapter.getItemCount()); // signal activity to dismiss progress dialog
    } else if (mFriends != null && mFriends.isEmpty()) {
      mCallback.onNoFriends();
    }
  }

  class FriendAdapter extends RecyclerView.Adapter<FriendHolder> {

    private List<User> mFriends;

    FriendAdapter(List<User> friends) {

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

      User friend = mFriends.get(position);
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

    private User mFriend;

    FriendHolder(LayoutInflater inflater, ViewGroup parent) {
      super(inflater.inflate(R.layout.friend_item, parent, false));

      itemView.setOnClickListener(this);
      mNameTextView = itemView.findViewById(R.id.friend_item_name);
      mStatusTextView = itemView.findViewById(R.id.friend_item_status);
      mLastKnownDateTextView = itemView.findViewById(R.id.friend_item_last_timestamp);
    }

    void bind(User friend) {

      mFriend = friend;
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
        List<String> locationKeys = new ArrayList<>();
        locationKeys.addAll(friend.LocationList.keySet());
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
