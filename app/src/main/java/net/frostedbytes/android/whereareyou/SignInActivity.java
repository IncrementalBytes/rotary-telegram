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

package net.frostedbytes.android.whereareyou;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.view.View;
import android.view.View.OnClickListener;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import net.frostedbytes.android.whereareyou.utils.LogUtils;

public class SignInActivity extends BaseActivity implements OnClickListener {

  private static final String TAG = SignInActivity.class.getSimpleName();

  private static final int RC_SIGN_IN = 4701;

  private FirebaseAuth mAuth;
  private GoogleApiClient mGoogleApiClient;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    LogUtils.debug(TAG, "++onCreate(Bundle)");
    setContentView(R.layout.activity_sign_in);

    SignInButton signInWithGoogleButton = findViewById(R.id.sign_in_button_google);
    signInWithGoogleButton.setOnClickListener(this);

    mAuth = FirebaseAuth.getInstance();

    GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
      .requestIdToken(getString(R.string.default_web_client_id))
      .requestEmail()
      .build();

    mGoogleApiClient = new GoogleApiClient.Builder(this)
      .enableAutoManage(this, connectionResult -> {
        LogUtils.debug(TAG, "++onConnectionFailed(ConnectionResult)");
        LogUtils.debug(
          TAG,
          "%s",
          connectionResult.getErrorMessage() != null ? connectionResult.getErrorMessage() : "Connection result was null.");
        Snackbar.make(
          findViewById(R.id.activity_sign_in),
          connectionResult.getErrorMessage() != null ? connectionResult.getErrorMessage() : "Connection result was null.",
          Snackbar.LENGTH_LONG).show();
      })
      .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
      .build();
  }

  @Override
  public void onStart() {
    super.onStart();

    LogUtils.debug(TAG, "++onStart()");
    if (BuildConfig.DEBUG) {
      LogUtils.debug(TAG, "Skipping auto-authentication.");
    } else {
      if (mAuth.getCurrentUser() != null) {
        onAuthenticateSuccess(mAuth.getCurrentUser());
      }
    }
  }

  @Override
  public void onClick(View view) {

    LogUtils.debug(TAG, "++onClick()");
    switch (view.getId()) {
      case R.id.sign_in_button_google:
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
        break;
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    LogUtils.debug(TAG, "++onActivityResult(%1d, %2d, Intent)", requestCode, resultCode);
    if (requestCode == RC_SIGN_IN) {
      GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
      if (result.isSuccess()) {
        GoogleSignInAccount account = result.getSignInAccount();
        showProgressDialog(getString(R.string.status_authenticating));
        if (account != null) {
          AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
          mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this, task -> {
              if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                onAuthenticateSuccess(mAuth.getCurrentUser());
              } else {
                LogUtils.error(
                  TAG,
                  "Authenticating with Google account failed: %1s",
                  task.getException() != null ? task.getException().getMessage() : "");
                Snackbar.make(findViewById(R.id.activity_sign_in), "Authenticating failed.", Snackbar.LENGTH_SHORT).show();
              }

              hideProgressDialog();
            });
        } else {
          LogUtils.error(TAG, "Unable to get sign-in account from authentication result.");
          Snackbar.make(findViewById(R.id.activity_sign_in), "Sign-in result was unexpected.", Snackbar.LENGTH_SHORT).show();
        }
      } else {
        LogUtils.error(TAG, "Getting task result failed: %1s", result.getStatus());
        Snackbar.make(findViewById(R.id.activity_sign_in), "Could not sign-in with Google.", Snackbar.LENGTH_SHORT).show();
      }
    }
  }

  private void onAuthenticateSuccess(FirebaseUser user) {

    LogUtils.debug(TAG, "++onAuthenticateSuccess(%1s)", user.getDisplayName());
    Intent intent = new Intent(SignInActivity.this, MainActivity.class);
    intent.putExtra(BaseActivity.ARG_USER_NAME, user.getDisplayName());
    intent.putExtra(BaseActivity.ARG_USER_ID, user.getUid());
    intent.putExtra(BaseActivity.ARG_EMAIL, user.getEmail());
    intent.putExtra(BaseActivity.ARG_PHOTO_URL, user.getPhotoUrl());
    startActivity(intent);
    finish();
  }
}
