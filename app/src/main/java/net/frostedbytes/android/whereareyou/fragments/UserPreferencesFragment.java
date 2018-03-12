package net.frostedbytes.android.whereareyou.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.preference.PreferenceFragmentCompat;
import net.frostedbytes.android.whereareyou.R;
import net.frostedbytes.android.whereareyou.utils.LogUtils;

public class UserPreferencesFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

  private static final String TAG = UserPreferencesFragment.class.getSimpleName();

  public static final String KEY_GET_FREQUENCY_SETTING = "preference_list_frequency";
  public static final String KEY_GET_HISTORY_SETTING = "preference_list_history";
  public static final String KEY_GET_SHARING_SETTING = "preference_switch_sharing";

  public interface OnPreferencesListener {

    void onPreferenceChanged();
  }

  private OnPreferencesListener mCallback;

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);

    LogUtils.debug(TAG, "++onAttach()");
    try {
      mCallback = (OnPreferencesListener) context;
    } catch (ClassCastException e) {
      throw new ClassCastException(context.toString() + " must implement onPreferenceChanged().");
    }
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

    LogUtils.debug(TAG, "++onCreatePreferences(Bundle, String)");
    addPreferencesFromResource(R.xml.app_preferences);
  }

  @Override
  public void onPause() {
    super.onPause();

    LogUtils.debug(TAG, "++onPause()");
    getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
  }

  @Override
  public void onResume() {
    super.onResume();

    LogUtils.debug(TAG, "++onResume()");
    getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String keyName) {

    LogUtils.debug(TAG, "++onSharedPreferenceChanged(SharedPreferences, String)");
    getPreferenceScreen().getSharedPreferences().edit().apply();
    if (keyName.equals(KEY_GET_FREQUENCY_SETTING) ||
      keyName.equals(KEY_GET_HISTORY_SETTING) ||
      keyName.equals(KEY_GET_SHARING_SETTING)) {
      mCallback.onPreferenceChanged();
    } else {
      LogUtils.error(TAG, "Unknown key: ", keyName);
    }
  }
}
