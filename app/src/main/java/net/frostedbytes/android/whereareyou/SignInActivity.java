package net.frostedbytes.android.whereareyou;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;
import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
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
import io.fabric.sdk.android.Fabric;
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

    // set up crashlytics, disabled for debug builds
    Crashlytics crashKit = new Crashlytics.Builder().core(new CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build()).build();

    // initialize fabric with the debug-disabled crashlytics
    Fabric.with(this, crashKit);

    signInWithGoogleButton.setOnClickListener(this);

    mAuth = FirebaseAuth.getInstance();

    GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
      .requestIdToken(getString(R.string.default_web_client_id))
      .requestEmail()
      .build();

    mGoogleApiClient = new GoogleApiClient.Builder(this)
      .enableAutoManage(this, connectionResult -> {
        LogUtils.debug(TAG, "++onConnectionFailed(ConnectionResult");
        LogUtils.debug(TAG, connectionResult.getErrorMessage());
        Toast.makeText(SignInActivity.this, connectionResult.getErrorMessage(), Toast.LENGTH_SHORT).show();
      })
      .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
      .build();
  }

  @Override
  public void onStart() {
    super.onStart();

    LogUtils.debug(TAG, "++onStart()");
    if (mAuth.getCurrentUser() != null) {
      onAuthenticateSuccess(mAuth.getCurrentUser());
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
              if (task.isSuccessful()) {
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
    startActivity(intent);
    finish();
  }
}
