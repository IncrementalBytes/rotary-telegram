package net.frostedbytes.android.whereareyou;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import net.frostedbytes.android.whereareyou.utils.LogUtils;

public class BaseActivity  extends AppCompatActivity {

  public static final String ARG_FRIEND_ID = "friend_id";
  public static final String ARG_USER_ID = "user_id";
  public static final String ARG_USER_NAME = "user_name";
  public static final String DEFAULT_ID = "000000000-0000-0000-0000-000000000000";

  private static final String TAG = BaseActivity.class.getSimpleName();

  private ProgressDialog mProgressDialog;

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);

    LogUtils.debug(TAG, "++onCreate(Bundle)");
  }

  void showProgressDialog(String message) {

    LogUtils.debug(TAG, "++showProgressDialog()");
    if (mProgressDialog == null) {
      mProgressDialog = new ProgressDialog(this);
      mProgressDialog.setCancelable(false);
      mProgressDialog.setMessage(message);
    }

    mProgressDialog.show();
  }

  void hideProgressDialog() {

    LogUtils.debug(TAG, "++hideProgressDialog()");
    if (mProgressDialog != null && mProgressDialog.isShowing()) {
      mProgressDialog.dismiss();
    }
  }
}
