package net.frostedbytes.android.whereareyou.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import net.frostedbytes.android.whereareyou.BaseActivity;
import net.frostedbytes.android.whereareyou.R;
import net.frostedbytes.android.whereareyou.utils.LogUtils;

public class ManageUserFragment extends Fragment {

  private static final String TAG = ManageUserFragment.class.getSimpleName();

  public interface OnManageUserListener {

  }

  private OnManageUserListener mCallback;

  private String mUserId;

  public static ManageUserFragment newInstance(String userId) {

    LogUtils.debug(TAG, "++newInstance(User)");
    ManageUserFragment fragment = new ManageUserFragment();
    Bundle args = new Bundle();
    args.putString(BaseActivity.ARG_USER_ID, userId);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    LogUtils.debug(TAG, "++onCreateView(LayoutInflater, ViewGroup, Bundle)");
    final View view = inflater.inflate(R.layout.fragment_manage_user, container, false);

    return view;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);

    LogUtils.debug(TAG, "++onAttach(Context)");
    try {
      mCallback = (OnManageUserListener) context;
    } catch (ClassCastException e) {
      throw new ClassCastException(context.toString() + " must implement TBD.");
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
  }
}
