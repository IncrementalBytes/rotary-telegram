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
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.HashMap;
import java.util.List;
import net.frostedbytes.android.whereareyou.BaseActivity;
import net.frostedbytes.android.whereareyou.R;
import net.frostedbytes.android.whereareyou.models.Friend;
import net.frostedbytes.android.whereareyou.models.User;
import net.frostedbytes.android.whereareyou.utils.LogUtils;
import net.frostedbytes.android.whereareyou.utils.PathUtils;

public class ManageUsersFragment extends Fragment {

  private static final String TAG = ManageUsersFragment.class.getSimpleName();

  private ViewPager mViewPager;

  private User mUser;

  private Query mQuery;
  private ListenerRegistration mListenerRegistration;

  public static ManageUsersFragment newInstance(String userId) {

    LogUtils.debug(TAG, "++newInstance(String)");
    ManageUsersFragment fragment = new ManageUsersFragment();
    Bundle args = new Bundle();
    args.putSerializable(BaseActivity.ARG_USER_ID, userId);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    LogUtils.debug(TAG, "++onCreateView(LayoutInflater, ViewGroup, Bundle)");
    View view = inflater.inflate(R.layout.fragment_view_pager, container, false);
    mViewPager = view.findViewById(R.id.manage_view_pager);
    PagerTabStrip pagerTabStrip = view.findViewById(R.id.manage_view_pager_header);

    pagerTabStrip.getChildAt(1).setPadding(30, 15, 30, 15);
    pagerTabStrip.setDrawFullUnderline(false);

    String queryPath = PathUtils.combine(User.USERS_ROOT, mUser.UserId, User.FRIEND_LIST);
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

        mUser.FriendList = new HashMap<>();
        List<DocumentSnapshot> documents = snapshot.getDocuments();
        if (!documents.isEmpty()) {
          for (DocumentSnapshot document : documents) {
            Friend friend = document.toObject(Friend.class);
            if (friend != null) {
              friend.UserId = document.getId();
              mUser.FriendList.put(friend.UserId, friend);
            }
          }
        }

        populateFriendData();
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
      mUser = new User();
      mUser.UserId = arguments.getString(BaseActivity.ARG_USER_ID);
    } else {
      LogUtils.debug(TAG, "Arguments were null.");
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

    mUser = null;
  }

  private void populateFriendData() {

    LogUtils.debug(TAG, "++populateFriendData()");
    mViewPager.setAdapter(new FragmentStatePagerAdapter(getChildFragmentManager()) {

      @Override
      public Fragment getItem(int position) {

        switch (position) {
          case 0:
            return FriendsPageFragment.newInstance(mUser);
          case 1:
            return RequestsPageFragment.newInstance(mUser);
          default:
            return null;
        }
      }

      @Override
      public int getCount() {

        return BaseActivity.NUM_USER_PAGES;
      }

      @Override
      public CharSequence getPageTitle(int position) {

        switch (position) {
          case 0:
            return "Friends";
          case 1:
            return "Requests";
          default:
            return null;
        }
      }
    });
  }
}
