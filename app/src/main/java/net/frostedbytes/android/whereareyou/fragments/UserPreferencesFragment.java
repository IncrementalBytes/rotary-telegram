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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.preference.PreferenceFragmentCompat;
import net.frostedbytes.android.whereareyou.R;
import net.frostedbytes.android.whereareyou.utils.LogUtils;

import static net.frostedbytes.android.whereareyou.BaseActivity.BASE_TAG;

public class UserPreferencesFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = BASE_TAG + UserPreferencesFragment.class.getSimpleName();

    public static final String KEY_GET_FREQUENCY_SETTING = "preference_list_frequency";

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
        if (keyName.equals(KEY_GET_FREQUENCY_SETTING)) {
            mCallback.onPreferenceChanged();
        } else {
            LogUtils.error(TAG, "Unknown key: ", keyName);
        }
    }
}
