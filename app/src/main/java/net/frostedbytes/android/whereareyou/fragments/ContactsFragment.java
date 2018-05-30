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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.frostedbytes.android.whereareyou.R;
import net.frostedbytes.android.whereareyou.models.User;
import net.frostedbytes.android.whereareyou.utils.LogUtils;

public class ContactsFragment extends Fragment {

  private static final String TAG = ContactsFragment.class.getSimpleName();

  public interface OnContactListListener {

    void onAddSharingContact(String contactName, String contactEmail);
  }

  private OnContactListListener mCallback;

  private RecyclerView mRecyclerView;

  private Map<String, User> mContactList;

  public static ContactsFragment newInstance() {

    LogUtils.debug(TAG, "++newInstance()");
    ContactsFragment fragment = new ContactsFragment();
    Bundle args = new Bundle();
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);

    LogUtils.debug(TAG, "++onAttach(Context)");
    try {
      mCallback = (OnContactListListener) context;
    } catch (ClassCastException e) {
      throw new ClassCastException("Not all callback methods have been implemented for " + context.toString());
    }
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    LogUtils.debug(TAG, "++onCreateView(LayoutInflater, ViewGroup, Bundle)");

    View view = inflater.inflate(R.layout.fragment_contacts_list, container, false);
    mRecyclerView = view.findViewById(R.id.contacts_list_view);
    if (getActivity() != null) {
      new FetchContactsTask(this).execute(getActivity().getContentResolver());
    } else {
      LogUtils.error(TAG, "Unable to get content resolver; cannot gather contacts.");
    }

    return view;
  }

  private void updateUI() {

    LogUtils.debug(TAG, "++updateUI()");
    ContactAdapter contactAdapter = new ContactAdapter(new ArrayList<>(mContactList.values()));
    mRecyclerView.setAdapter(contactAdapter);
    mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
  }

  class ContactAdapter extends RecyclerView.Adapter<ContactHolder> {

    private List<User> mContacts;

    ContactAdapter(List<User> contacts) {

      mContacts = contacts;
    }

    @NonNull
    @Override
    public ContactHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

      LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
      return new ContactHolder(layoutInflater, parent);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactHolder holder, int position) {

      User contact = mContacts.get(position);
      holder.bind(contact);
    }

    @Override
    public int getItemCount() {
      return mContacts.size();
    }
  }

  class ContactHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

    private final TextView mNameTextView;
    private final TextView mEmailTextView;

    private User mContact;

    ContactHolder(LayoutInflater inflater, ViewGroup parent) {
      super(inflater.inflate(R.layout.contacts_item, parent, false));

      itemView.setOnClickListener(this);
      mNameTextView = itemView.findViewById(R.id.contacts_text_name_value);
      mEmailTextView = itemView.findViewById(R.id.contacts_text_email_value);
    }

    void bind(User contact) {

      mContact = contact;
      mNameTextView.setText(mContact.FullName);
      mEmailTextView.setText(mContact.Email);
    }

    @Override
    public void onClick(View view) {

      if (mContact.Emails.size() > 1) { // give user a selection of all emails
        if (getActivity() != null) {
          AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
          final CharSequence[] emails = mContact.Emails.toArray(new CharSequence[mContact.Emails.size()]);
          builder.setTitle(R.string.pick_contact)
            .setItems(emails, (dialog, which) -> {
              LogUtils.debug(TAG, "Selected %s", mContact.Emails.get(which));
              mCallback.onAddSharingContact(mContact.FullName, mContact.Emails.get(which));
            }).create();
        } else {
          mCallback.onAddSharingContact(mContact.FullName, mContact.Email);
        }
      } else {
        LogUtils.error(TAG, "Could not get activity from ContactsFragment.");
      }
    }
  }

  private static class FetchContactsTask extends AsyncTask<Object, Void, Map<String, User>> {

    private WeakReference<ContactsFragment> mFragmentWeakReference;

    FetchContactsTask(ContactsFragment context) {

      mFragmentWeakReference = new WeakReference<>(context);
    }

    @Override
    protected Map<String, User> doInBackground(Object... params) {

      LogUtils.debug(TAG, "++doInBackground(Object... params)");
      Map<String, User> users = new HashMap<>();
      ContentResolver contentResolver = (ContentResolver)params[0];
      if (contentResolver == null) {
        LogUtils.warn(TAG, "ContentResolver unexpected.");
        return users;
      }

      Cursor cursor = contentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
      if (cursor != null && cursor.getCount() > 0) {
        while (cursor.moveToNext()) { // loop for every contact in the phone
          User user = new User();
          user.UserId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
          user.FullName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));

          // Query and loop for every email of the contact
          Cursor emailCursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            null,
            ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
            new String[]{user.UserId},
            null);
          if (emailCursor != null && emailCursor.getCount() > 0) {
            while (emailCursor.moveToNext()) {
              user.Email = emailCursor.getString(emailCursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA));
              user.Emails.add(user.Email);
            }

            emailCursor.close();
            users.put(user.UserId, user);
          } else {
            LogUtils.error(TAG, "Skipping user with no email addresses.");
          }
        }

        cursor.close();
      } else {
        LogUtils.error(TAG, "Unable to query contacts.");
      }

      return users;
    }

    @Override
    protected void onPostExecute(Map<String, User> contacts) {

      LogUtils.debug(TAG, "++onPostExecute((Map<String, User>)");
      ContactsFragment fragment = mFragmentWeakReference.get();
      if (fragment == null || fragment.isDetached()) {
        LogUtils.debug(TAG, "Fragment is null or detached.");
        return;
      }

      fragment.mContactList = contacts;
      fragment.updateUI();
    }
  }
}
