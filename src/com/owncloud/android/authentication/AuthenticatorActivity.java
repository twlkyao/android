/* ownCloud Android client application
 *   Copyright (C) 2012  Bartek Przybylski
 *   Copyright (C) 2012-2013 ownCloud Inc.
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License version 2,
 *   as published by the Free Software Foundation.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.owncloud.android.authentication;

import com.owncloud.android.Log_OC;
import com.owncloud.android.ui.dialog.SslValidatorDialog;
import com.owncloud.android.ui.dialog.SslValidatorDialog.OnSslValidatorListener;
import com.owncloud.android.utils.OwnCloudVersion;
import com.owncloud.android.network.OwnCloudClientUtils;
import com.owncloud.android.operations.OwnCloudServerCheckOperation;
import com.owncloud.android.operations.ExistenceCheckOperation;
import com.owncloud.android.operations.OAuth2GetAccessToken;
import com.owncloud.android.operations.OnRemoteOperationListener;
import com.owncloud.android.operations.RemoteOperation;
import com.owncloud.android.operations.RemoteOperationResult;
import com.owncloud.android.operations.RemoteOperationResult.ResultCode;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;

import com.owncloud.android.R;

import eu.alefzero.webdav.WebdavClient;

/**
 * This Activity is used to add an ownCloud account to the App
 * 
 * @author Bartek Przybylski
 * @author David A. Velasco
 */
public class AuthenticatorActivity extends AccountAuthenticatorActivity
implements  OnRemoteOperationListener, OnSslValidatorListener, OnFocusChangeListener, OnEditorActionListener {

    private static final String TAG = AuthenticatorActivity.class.getSimpleName();

    public static final String EXTRA_ACCOUNT = "ACCOUNT";
    public static final String EXTRA_USER_NAME = "USER_NAME";
    public static final String EXTRA_HOST_NAME = "HOST_NAME";
    public static final String EXTRA_ACTION = "ACTION";
    public static final String EXTRA_ENFORCED_UPDATE = "ENFORCE_UPDATE";

    private static final String KEY_HOST_URL_TEXT = "HOST_URL_TEXT";
    private static final String KEY_OC_VERSION = "OC_VERSION";
    private static final String KEY_ACCOUNT = "ACCOUNT";
    private static final String KEY_SERVER_VALID = "SERVER_VALID";
    private static final String KEY_SERVER_CHECKED = "SERVER_CHECKED";
    private static final String KEY_SERVER_CHECK_IN_PROGRESS = "SERVER_CHECK_IN_PROGRESS"; 
    private static final String KEY_SERVER_STATUS_TEXT = "SERVER_STATUS_TEXT";
    private static final String KEY_SERVER_STATUS_ICON = "SERVER_STATUS_ICON";
    private static final String KEY_IS_SSL_CONN = "IS_SSL_CONN";
    private static final String KEY_PASSWORD_VISIBLE = "PASSWORD_VISIBLE";
    private static final String KEY_AUTH_STATUS_TEXT = "AUTH_STATUS_TEXT";
    private static final String KEY_AUTH_STATUS_ICON = "AUTH_STATUS_ICON";
    private static final String KEY_REFRESH_BUTTON_ENABLED = "KEY_REFRESH_BUTTON_ENABLED";

    private static final String OAUTH_MODE_ON = "on";
    private static final String OAUTH_MODE_OFF = "off";
    private static final String OAUTH_MODE_OPTIONAL = "optional";

    private static final int DIALOG_LOGIN_PROGRESS = 0;
    private static final int DIALOG_SSL_VALIDATOR = 1;
    private static final int DIALOG_CERT_NOT_SAVED = 2;
    private static final int DIALOG_OAUTH2_LOGIN_PROGRESS = 3;

    public static final byte ACTION_CREATE = 0;
    public static final byte ACTION_UPDATE_TOKEN = 1;

    private String mHostBaseUrl;
    private OwnCloudVersion mDiscoveredVersion;

    private int mServerStatusText, mServerStatusIcon;
    private boolean mServerIsChecked, mServerIsValid, mIsSslConn;
    private int mAuthStatusText, mAuthStatusIcon;    

    private final Handler mHandler = new Handler();
    private Thread mOperationThread;
    private OwnCloudServerCheckOperation mOcServerChkOperation;
    private ExistenceCheckOperation mAuthCheckOperation;
    private RemoteOperationResult mLastSslUntrustedServerResult;

    private Uri mNewCapturedUriFromOAuth2Redirection;

    private AccountManager mAccountMgr;
    private boolean mJustCreated;
    private byte mAction;
    private Account mAccount;

    private EditText mHostUrlInput;
    private View mRefreshButton;
    private EditText mUsernameInput;
    private EditText mPasswordInput;
    private CheckBox mOAuth2Check;
    private String mOAuthAccessToken;
    private View mOkButton;
    private TextView mAuthStatusLayout;

    private TextView mOAuthAuthEndpointText;
    private TextView mOAuthTokenEndpointText;
    
    private boolean mRefreshButtonEnabled;
    
    private boolean mHostUrlInputEnabled;


    /**
     * {@inheritDoc}
     * 
     * IMPORTANT ENTRY POINT 1: activity is shown to the user
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        /// set view and get references to view elements
        setContentView(R.layout.account_setup);
        mHostUrlInput = (EditText) findViewById(R.id.hostUrlInput);
        mHostUrlInput.setText(getString(R.string.server_url));  // valid although R.string.server_url is an empty string
        mUsernameInput = (EditText) findViewById(R.id.account_username);
        mPasswordInput = (EditText) findViewById(R.id.account_password);
        mOAuthAuthEndpointText = (TextView)findViewById(R.id.oAuthEntryPoint_1);
        mOAuthTokenEndpointText = (TextView)findViewById(R.id.oAuthEntryPoint_2);
        mOAuth2Check = (CheckBox) findViewById(R.id.oauth_onOff_check);
        mOkButton = findViewById(R.id.buttonOK);
        mAuthStatusLayout = (TextView) findViewById(R.id.auth_status_text); 
        
        /// set Host Url Input Enabled
        mHostUrlInputEnabled = getResources().getBoolean(R.bool.show_server_url_input);
        

        /// complete label for 'register account' button
        Button b = (Button) findViewById(R.id.account_register);
        if (b != null) {
            b.setText(String.format(getString(R.string.auth_register), getString(R.string.app_name)));
        }

        /// initialization
        mAccountMgr = AccountManager.get(this);
        mNewCapturedUriFromOAuth2Redirection = null;
        mAction = getIntent().getByteExtra(EXTRA_ACTION, ACTION_CREATE); 
        mAccount = null;
        mHostBaseUrl = "";
        boolean refreshButtonEnabled = false;
        
        // URL input configuration applied
        if (!mHostUrlInputEnabled)
        {
            findViewById(R.id.hostUrlFrame).setVisibility(View.GONE);
            mRefreshButton = findViewById(R.id.centeredRefreshButton);

        } else {
            mRefreshButton = findViewById(R.id.embeddedRefreshButton);
        }

        if (savedInstanceState == null) {
            /// connection state and info
            mServerStatusText = mServerStatusIcon = 0;
            mServerIsValid = false;
            mServerIsChecked = false;
            mIsSslConn = false;
            mAuthStatusText = mAuthStatusIcon = 0;

            /// retrieve extras from intent
            String tokenType = getIntent().getExtras().getString(AccountAuthenticator.KEY_AUTH_TOKEN_TYPE);
            boolean oAuthRequired = AccountAuthenticator.AUTH_TOKEN_TYPE_ACCESS_TOKEN.equals(tokenType) || OAUTH_MODE_ON.equals(getString(R.string.oauth2_mode));

            mAccount = getIntent().getExtras().getParcelable(EXTRA_ACCOUNT);
            if (mAccount != null) {
                String ocVersion = mAccountMgr.getUserData(mAccount, AccountAuthenticator.KEY_OC_VERSION);
                if (ocVersion != null) {
                    mDiscoveredVersion = new OwnCloudVersion(ocVersion);
                }
                mHostBaseUrl = normalizeUrl(mAccountMgr.getUserData(mAccount, AccountAuthenticator.KEY_OC_BASE_URL));
                mHostUrlInput.setText(mHostBaseUrl);
                String userName = mAccount.name.substring(0, mAccount.name.lastIndexOf('@'));
                mUsernameInput.setText(userName);
                oAuthRequired = (mAccountMgr.getUserData(mAccount, AccountAuthenticator.KEY_SUPPORTS_OAUTH2) != null);
            }
            mOAuth2Check.setChecked(oAuthRequired);
            changeViewByOAuth2Check(oAuthRequired);
            mJustCreated = true;
            
            if (mAction == ACTION_UPDATE_TOKEN || !mHostUrlInputEnabled) {
                checkOcServer(); 
            }

        } else {
            /// connection state and info
            mServerIsValid = savedInstanceState.getBoolean(KEY_SERVER_VALID);
            mServerIsChecked = savedInstanceState.getBoolean(KEY_SERVER_CHECKED);
            mServerStatusText = savedInstanceState.getInt(KEY_SERVER_STATUS_TEXT);
            mServerStatusIcon = savedInstanceState.getInt(KEY_SERVER_STATUS_ICON);
            mIsSslConn = savedInstanceState.getBoolean(KEY_IS_SSL_CONN);
            mAuthStatusText = savedInstanceState.getInt(KEY_AUTH_STATUS_TEXT);
            mAuthStatusIcon = savedInstanceState.getInt(KEY_AUTH_STATUS_ICON);
            if (savedInstanceState.getBoolean(KEY_PASSWORD_VISIBLE, false)) {
                showPassword();
            }
            
            /// server data
            String ocVersion = savedInstanceState.getString(KEY_OC_VERSION);
            if (ocVersion != null) {
                mDiscoveredVersion = new OwnCloudVersion(ocVersion);
            }
            mHostBaseUrl = savedInstanceState.getString(KEY_HOST_URL_TEXT);

            // account data, if updating
            mAccount = savedInstanceState.getParcelable(KEY_ACCOUNT);

            // check if server check was interrupted by a configuration change
            if (savedInstanceState.getBoolean(KEY_SERVER_CHECK_IN_PROGRESS, false)) {
                checkOcServer();
            }            
            
            // refresh button enabled
            refreshButtonEnabled = savedInstanceState.getBoolean(KEY_REFRESH_BUTTON_ENABLED);
            

        }

        showServerStatus();
        showAuthStatus();

        if (mAction == ACTION_UPDATE_TOKEN) {
            /// lock things that should not change
            mHostUrlInput.setEnabled(false);
            mHostUrlInput.setFocusable(false);
            mUsernameInput.setEnabled(false);
            mUsernameInput.setFocusable(false);
            mOAuth2Check.setVisibility(View.GONE);
        }
        
        //if (mServerIsChecked && !mServerIsValid && mRefreshButtonEnabled) showRefreshButton();
        if (mServerIsChecked && !mServerIsValid && refreshButtonEnabled) showRefreshButton();
        mOkButton.setEnabled(mServerIsValid); // state not automatically recovered in configuration changes

        if (!OAUTH_MODE_OPTIONAL.equals(getString(R.string.oauth2_mode))) {
            mOAuth2Check.setVisibility(View.GONE);
        }

        mPasswordInput.setText("");     // clean password to avoid social hacking (disadvantage: password in removed if the device is turned aside)

        /// bind view elements to listeners
        mHostUrlInput.setOnFocusChangeListener(this);
        mHostUrlInput.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                if (!mHostBaseUrl.equals(normalizeUrl(mHostUrlInput.getText().toString()))) {
                    mOkButton.setEnabled(false);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

        });
        mPasswordInput.setOnFocusChangeListener(this);
        mPasswordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        mPasswordInput.setOnEditorActionListener(this);
        mPasswordInput.setOnTouchListener(new RightDrawableOnTouchListener() {
            @Override
            public boolean onDrawableTouch(final MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    AuthenticatorActivity.this.onViewPasswordClick();
                }
                return true;
            }
        });
    }

    /**
     * Saves relevant state before {@link #onPause()}
     * 
     * Do NOT save {@link #mNewCapturedUriFromOAuth2Redirection}; it keeps a temporal flag, intended to defer the 
     * processing of the redirection caught in {@link #onNewIntent(Intent)} until {@link #onResume()} 
     * 
     * See {@link #loadSavedInstanceState(Bundle)}
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        /// connection state and info
        outState.putInt(KEY_SERVER_STATUS_TEXT, mServerStatusText);
        outState.putInt(KEY_SERVER_STATUS_ICON, mServerStatusIcon);
        outState.putBoolean(KEY_SERVER_VALID, mServerIsValid);
        outState.putBoolean(KEY_SERVER_CHECKED, mServerIsChecked);
        outState.putBoolean(KEY_SERVER_CHECK_IN_PROGRESS, (!mServerIsValid && mOcServerChkOperation != null));
        outState.putBoolean(KEY_IS_SSL_CONN, mIsSslConn);
        outState.putBoolean(KEY_PASSWORD_VISIBLE, isPasswordVisible());
        outState.putInt(KEY_AUTH_STATUS_ICON, mAuthStatusIcon);
        outState.putInt(KEY_AUTH_STATUS_TEXT, mAuthStatusText);

        /// server data
        if (mDiscoveredVersion != null) {
            outState.putString(KEY_OC_VERSION, mDiscoveredVersion.toString());
        }
        outState.putString(KEY_HOST_URL_TEXT, mHostBaseUrl);

        /// account data, if updating
        if (mAccount != null) {
            outState.putParcelable(KEY_ACCOUNT, mAccount);
        }
        
        // refresh button enabled
        //outState.putBoolean(KEY_REFRESH_BUTTON_ENABLED, mRefreshButtonEnabled);
        outState.putBoolean(KEY_REFRESH_BUTTON_ENABLED, (mRefreshButton.getVisibility() == View.VISIBLE));

    }


    /**
     * The redirection triggered by the OAuth authentication server as response to the GET AUTHORIZATION request
     * is caught here.
     * 
     * To make this possible, this activity needs to be qualified with android:launchMode = "singleTask" in the
     * AndroidManifest.xml file.
     */
    @Override
    protected void onNewIntent (Intent intent) {
        Log_OC.d(TAG, "onNewIntent()");
        Uri data = intent.getData();
        if (data != null && data.toString().startsWith(getString(R.string.oauth2_redirect_uri))) {
            mNewCapturedUriFromOAuth2Redirection = data;
        }
    }


    /**
     * The redirection triggered by the OAuth authentication server as response to the GET AUTHORIZATION, and 
     * deferred in {@link #onNewIntent(Intent)}, is processed here.
     */
    @Override
    protected void onResume() {
        super.onResume();
        // the state of mOAuth2Check is automatically recovered between configuration changes, but not before onCreate() finishes; so keep the next lines here
        changeViewByOAuth2Check(mOAuth2Check.isChecked());  
        if (mAction == ACTION_UPDATE_TOKEN && mJustCreated && getIntent().getBooleanExtra(EXTRA_ENFORCED_UPDATE, false)) {
            if (mOAuth2Check.isChecked())
                Toast.makeText(this, R.string.auth_expired_oauth_token_toast, Toast.LENGTH_LONG).show();
            else
                Toast.makeText(this, R.string.auth_expired_basic_auth_toast, Toast.LENGTH_LONG).show();
        }

        if (mNewCapturedUriFromOAuth2Redirection != null) {
            getOAuth2AccessTokenFromCapturedRedirection();            
        }

        mJustCreated = false;
    }


    /**
     * Parses the redirection with the response to the GET AUTHORIZATION request to the 
     * oAuth server and requests for the access token (GET ACCESS TOKEN)
     */
    private void getOAuth2AccessTokenFromCapturedRedirection() {
        /// Parse data from OAuth redirection
        String queryParameters = mNewCapturedUriFromOAuth2Redirection.getQuery();
        mNewCapturedUriFromOAuth2Redirection = null;

        /// Showing the dialog with instructions for the user.
        showDialog(DIALOG_OAUTH2_LOGIN_PROGRESS);

        /// GET ACCESS TOKEN to the oAuth server 
        RemoteOperation operation = new OAuth2GetAccessToken(   getString(R.string.oauth2_client_id), 
                getString(R.string.oauth2_redirect_uri),       
                getString(R.string.oauth2_grant_type),
                queryParameters);
        //WebdavClient client = OwnCloudClientUtils.createOwnCloudClient(Uri.parse(getString(R.string.oauth2_url_endpoint_access)), getApplicationContext());
        WebdavClient client = OwnCloudClientUtils.createOwnCloudClient(Uri.parse(mOAuthTokenEndpointText.getText().toString().trim()), getApplicationContext());
        operation.execute(client, this, mHandler);
    }



    /**
     * Handles the change of focus on the text inputs for the server URL and the password
     */
    public void onFocusChange(View view, boolean hasFocus) {
        if (view.getId() == R.id.hostUrlInput) {   
            if (!hasFocus) {
                onUrlInputFocusLost((TextView) view);
            }
            else {
                hideRefreshButton();
            }

        } else if (view.getId() == R.id.account_password) {
            onPasswordFocusChanged((TextView) view, hasFocus);
        }
    }


    /**
     * Handles changes in focus on the text input for the server URL.
     * 
     * IMPORTANT ENTRY POINT 2: When (!hasFocus), user wrote the server URL and changed to 
     * other field. The operation to check the existence of the server in the entered URL is
     * started. 
     * 
     * When hasFocus:    user 'comes back' to write again the server URL.
     * 
     * @param hostInput     TextView with the URL input field receiving the change of focus.
     */
    private void onUrlInputFocusLost(TextView hostInput) {
        if (!mHostBaseUrl.equals(normalizeUrl(mHostUrlInput.getText().toString()))) {
            checkOcServer();
        } else {
            mOkButton.setEnabled(mServerIsValid);
            if (!mServerIsValid) {
                showRefreshButton();
            }
        }
    }


    private void checkOcServer() {
        String uri = trimUrlWebdav(mHostUrlInput.getText().toString().trim());
        
        if (!mHostUrlInputEnabled){
            uri = getString(R.string.server_url);
        }
        
        mServerIsValid = false;
        mServerIsChecked = false;
        mOkButton.setEnabled(false);
        mDiscoveredVersion = null;
        hideRefreshButton();
        if (uri.length() != 0) {
            mServerStatusText = R.string.auth_testing_connection;
            mServerStatusIcon = R.drawable.progress_small;
            showServerStatus();
            mOcServerChkOperation = new  OwnCloudServerCheckOperation(uri, this);
            WebdavClient client = OwnCloudClientUtils.createOwnCloudClient(Uri.parse(uri), this);
            mOperationThread = mOcServerChkOperation.execute(client, this, mHandler);
        } else {
            mServerStatusText = 0;
            mServerStatusIcon = 0;
            showServerStatus();
        }
    }


    /**
     * Handles changes in focus on the text input for the password (basic authorization).
     * 
     * When (hasFocus), the button to toggle password visibility is shown.
     * 
     * When (!hasFocus), the button is made invisible and the password is hidden.
     * 
     * @param passwordInput    TextView with the password input field receiving the change of focus.
     * @param hasFocus          'True' if focus is received, 'false' if is lost
     */
    private void onPasswordFocusChanged(TextView passwordInput, boolean hasFocus) {
        if (hasFocus) {
            showViewPasswordButton();
        } else {
            hidePassword();
            hidePasswordButton();
        }
    }


    private void showViewPasswordButton() {
        //int drawable = android.R.drawable.ic_menu_view;
        int drawable = R.drawable.ic_view;
        if (isPasswordVisible()) {
            //drawable = android.R.drawable.ic_secure;
            drawable = R.drawable.ic_hide;
        }
        mPasswordInput.setCompoundDrawablesWithIntrinsicBounds(0, 0, drawable, 0);
    }

    private boolean isPasswordVisible() {
        return ((mPasswordInput.getInputType() & InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
    }
    
    private void hidePasswordButton() {
        mPasswordInput.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
    }

    private void showPassword() {
        mPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        showViewPasswordButton();
    }
    
    private void hidePassword() {
        mPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        showViewPasswordButton();
    }
    
    
    /**
     * Cancels the authenticator activity
     * 
     * IMPORTANT ENTRY POINT 3: Never underestimate the importance of cancellation
     * 
     * This method is bound in the layout/acceoun_setup.xml resource file.
     * 
     * @param view      Cancel button
     */
    public void onCancelClick(View view) {
        setResult(RESULT_CANCELED);     // TODO review how is this related to AccountAuthenticator (debugging)
        finish();
    }



    /**
     * Checks the credentials of the user in the root of the ownCloud server
     * before creating a new local account.
     * 
     * For basic authorization, a check of existence of the root folder is
     * performed.
     * 
     * For OAuth, starts the flow to get an access token; the credentials test 
     * is postponed until it is available.
     * 
     * IMPORTANT ENTRY POINT 4
     * 
     * @param view      OK button
     */
    public void onOkClick(View view) {
        // this check should be unnecessary
        if (mDiscoveredVersion == null || !mDiscoveredVersion.isVersionValid()  || mHostBaseUrl == null || mHostBaseUrl.length() == 0) {
            mServerStatusIcon = R.drawable.common_error;
            mServerStatusText = R.string.auth_wtf_reenter_URL;
            showServerStatus();
            mOkButton.setEnabled(false);
            Log_OC.wtf(TAG,  "The user was allowed to click 'connect' to an unchecked server!!");
            return;
        }

        if (mOAuth2Check.isChecked()) {
            startOauthorization();

        } else {
            checkBasicAuthorization();
        }
    }


    /**
     * Tests the credentials entered by the user performing a check of existence on 
     * the root folder of the ownCloud server.
     */
    private void checkBasicAuthorization() {
        /// get the path to the root folder through WebDAV from the version server
        String webdav_path = AccountUtils.getWebdavPath(mDiscoveredVersion, false);

        /// get basic credentials entered by user
        String username = mUsernameInput.getText().toString();
        String password = mPasswordInput.getText().toString();

        /// be gentle with the user
        showDialog(DIALOG_LOGIN_PROGRESS);

        /// test credentials accessing the root folder
        mAuthCheckOperation = new  ExistenceCheckOperation("", this, false);
        WebdavClient client = OwnCloudClientUtils.createOwnCloudClient(Uri.parse(mHostBaseUrl + webdav_path), this);
        client.setBasicCredentials(username, password);
        mOperationThread = mAuthCheckOperation.execute(client, this, mHandler);
    }


    /**
     * Starts the OAuth 'grant type' flow to get an access token, with 
     * a GET AUTHORIZATION request to the BUILT-IN authorization server. 
     */
    private void startOauthorization() {
        // be gentle with the user
        mAuthStatusIcon = R.drawable.progress_small;
        mAuthStatusText = R.string.oauth_login_connection;
        showAuthStatus();

        // GET AUTHORIZATION request
        //Uri uri = Uri.parse(getString(R.string.oauth2_url_endpoint_auth));
        Uri uri = Uri.parse(mOAuthAuthEndpointText.getText().toString().trim());
        Uri.Builder uriBuilder = uri.buildUpon();
        uriBuilder.appendQueryParameter(OAuth2Constants.KEY_RESPONSE_TYPE, getString(R.string.oauth2_response_type));
        uriBuilder.appendQueryParameter(OAuth2Constants.KEY_REDIRECT_URI, getString(R.string.oauth2_redirect_uri));   
        uriBuilder.appendQueryParameter(OAuth2Constants.KEY_CLIENT_ID, getString(R.string.oauth2_client_id));
        uriBuilder.appendQueryParameter(OAuth2Constants.KEY_SCOPE, getString(R.string.oauth2_scope));
        //uriBuilder.appendQueryParameter(OAuth2Constants.KEY_STATE, whateverwewant);
        uri = uriBuilder.build();
        Log_OC.d(TAG, "Starting browser to view " + uri.toString());
        Intent i = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(i);
    }


    /**
     * Callback method invoked when a RemoteOperation executed by this Activity finishes.
     * 
     * Dispatches the operation flow to the right method.
     */
    @Override
    public void onRemoteOperationFinish(RemoteOperation operation, RemoteOperationResult result) {

        if (operation instanceof OwnCloudServerCheckOperation) {
            onOcServerCheckFinish((OwnCloudServerCheckOperation) operation, result);

        } else if (operation instanceof OAuth2GetAccessToken) {
            onGetOAuthAccessTokenFinish((OAuth2GetAccessToken)operation, result);

        } else if (operation instanceof ExistenceCheckOperation)  {
            onAuthorizationCheckFinish((ExistenceCheckOperation)operation, result);

        }
    }


    /**
     * Processes the result of the server check performed when the user finishes the enter of the
     * server URL.
     * 
     * @param operation     Server check performed.
     * @param result        Result of the check.
     */
    private void onOcServerCheckFinish(OwnCloudServerCheckOperation operation, RemoteOperationResult result) {
        if (operation.equals(mOcServerChkOperation)) {
            /// save result state
            mServerIsChecked = true;
            mServerIsValid = result.isSuccess();
            mIsSslConn = (result.getCode() == ResultCode.OK_SSL);
            mOcServerChkOperation = null;

            /// update status icon and text
            if (mServerIsValid) {
                hideRefreshButton();
            } else {
                showRefreshButton();
            }
            updateServerStatusIconAndText(result);
            showServerStatus();

            /// very special case (TODO: move to a common place for all the remote operations)
            if (result.getCode() == ResultCode.SSL_RECOVERABLE_PEER_UNVERIFIED) {
                mLastSslUntrustedServerResult = result;
                showDialog(DIALOG_SSL_VALIDATOR); 
            }

            /// retrieve discovered version and normalize server URL
            mDiscoveredVersion = operation.getDiscoveredVersion();
            mHostBaseUrl = normalizeUrl(mHostUrlInput.getText().toString());

            /// allow or not the user try to access the server
            mOkButton.setEnabled(mServerIsValid);

        }   // else nothing ; only the last check operation is considered; 
        // multiple can be triggered if the user amends a URL before a previous check can be triggered
    }


    private String normalizeUrl(String url) {
        if (url != null && url.length() > 0) {
            url = url.trim();
            if (!url.toLowerCase().startsWith("http://") &&
                    !url.toLowerCase().startsWith("https://")) {
                if (mIsSslConn) {
                    url = "https://" + url;
                } else {
                    url = "http://" + url;
                }
            }

            // OC-208: Add suffix remote.php/webdav to normalize (OC-34)            
            url = trimUrlWebdav(url);

            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }

        }
        Log_OC.d(TAG, "URL Normalize " + url);
        return (url != null ? url : "");
    }


    private String trimUrlWebdav(String url){       
        if(url.toLowerCase().endsWith(AccountUtils.WEBDAV_PATH_4_0)){
            url = url.substring(0, url.length() - AccountUtils.WEBDAV_PATH_4_0.length());             
        } else if(url.toLowerCase().endsWith(AccountUtils.WEBDAV_PATH_2_0)){
            url = url.substring(0, url.length() - AccountUtils.WEBDAV_PATH_2_0.length());             
        } else if (url.toLowerCase().endsWith(AccountUtils.WEBDAV_PATH_1_2)){
            url = url.substring(0, url.length() - AccountUtils.WEBDAV_PATH_1_2.length());             
        } 
        return (url != null ? url : "");
    }
    
    
    /**
     * Chooses the right icon and text to show to the user for the received operation result.
     * 
     * @param result    Result of a remote operation performed in this activity
     */
    private void updateServerStatusIconAndText(RemoteOperationResult result) {
        mServerStatusIcon = R.drawable.common_error;    // the most common case in the switch below

        switch (result.getCode()) {
        case OK_SSL:
            mServerStatusIcon = android.R.drawable.ic_secure;
            mServerStatusText = R.string.auth_secure_connection;
            break;

        case OK_NO_SSL:
        case OK:
            if (mHostUrlInput.getText().toString().trim().toLowerCase().startsWith("http://") ) {
                mServerStatusText = R.string.auth_connection_established;
                mServerStatusIcon = R.drawable.ic_ok;
            } else {
                mServerStatusText = R.string.auth_nossl_plain_ok_title;
                mServerStatusIcon = android.R.drawable.ic_partial_secure;
            }
            break;

        case NO_NETWORK_CONNECTION:
            mServerStatusIcon = R.drawable.no_network;
            mServerStatusText = R.string.auth_no_net_conn_title;
            break;

        case SSL_RECOVERABLE_PEER_UNVERIFIED:
            mServerStatusText = R.string.auth_ssl_unverified_server_title;
            break;
        case BAD_OC_VERSION:
            mServerStatusText = R.string.auth_bad_oc_version_title;
            break;
        case WRONG_CONNECTION:
            mServerStatusText = R.string.auth_wrong_connection_title;
            break;
        case TIMEOUT:
            mServerStatusText = R.string.auth_timeout_title;
            break;
        case INCORRECT_ADDRESS:
            mServerStatusText = R.string.auth_incorrect_address_title;
            break;
        case SSL_ERROR:
            mServerStatusText = R.string.auth_ssl_general_error_title;
            break;
        case UNAUTHORIZED:
            mServerStatusText = R.string.auth_unauthorized;
            break;
        case HOST_NOT_AVAILABLE:
            mServerStatusText = R.string.auth_unknown_host_title;
            break;
        case INSTANCE_NOT_CONFIGURED:
            mServerStatusText = R.string.auth_not_configured_title;
            break;
        case FILE_NOT_FOUND:
            mServerStatusText = R.string.auth_incorrect_path_title;
            break;
        case OAUTH2_ERROR:
            mServerStatusText = R.string.auth_oauth_error;
            break;
        case OAUTH2_ERROR_ACCESS_DENIED:
            mServerStatusText = R.string.auth_oauth_error_access_denied;
            break;
        case UNHANDLED_HTTP_CODE:
        case UNKNOWN_ERROR:
            mServerStatusText = R.string.auth_unknown_error_title;
            break;
        default:
            mServerStatusText = 0;
            mServerStatusIcon = 0;
        }
    }


    /**
     * Chooses the right icon and text to show to the user for the received operation result.
     * 
     * @param result    Result of a remote operation performed in this activity
     */
    private void updateAuthStatusIconAndText(RemoteOperationResult result) {
        mAuthStatusIcon = R.drawable.common_error;    // the most common case in the switch below

        switch (result.getCode()) {
        case OK_SSL:
            mAuthStatusIcon = android.R.drawable.ic_secure;
            mAuthStatusText = R.string.auth_secure_connection;
            break;

        case OK_NO_SSL:
        case OK:
            if (mHostUrlInput.getText().toString().trim().toLowerCase().startsWith("http://") ) {
                mAuthStatusText = R.string.auth_connection_established;
                mAuthStatusIcon = R.drawable.ic_ok;
            } else {
                mAuthStatusText = R.string.auth_nossl_plain_ok_title;
                mAuthStatusIcon = android.R.drawable.ic_partial_secure;
            }
            break;

        case NO_NETWORK_CONNECTION:
            mAuthStatusIcon = R.drawable.no_network;
            mAuthStatusText = R.string.auth_no_net_conn_title;
            break;

        case SSL_RECOVERABLE_PEER_UNVERIFIED:
            mAuthStatusText = R.string.auth_ssl_unverified_server_title;
            break;
        case BAD_OC_VERSION:
            mAuthStatusText = R.string.auth_bad_oc_version_title;
            break;
        case WRONG_CONNECTION:
            mAuthStatusText = R.string.auth_wrong_connection_title;
            break;
        case TIMEOUT:
            mAuthStatusText = R.string.auth_timeout_title;
            break;
        case INCORRECT_ADDRESS:
            mAuthStatusText = R.string.auth_incorrect_address_title;
            break;
        case SSL_ERROR:
            mAuthStatusText = R.string.auth_ssl_general_error_title;
            break;
        case UNAUTHORIZED:
            mAuthStatusText = R.string.auth_unauthorized;
            break;
        case HOST_NOT_AVAILABLE:
            mAuthStatusText = R.string.auth_unknown_host_title;
            break;
        case INSTANCE_NOT_CONFIGURED:
            mAuthStatusText = R.string.auth_not_configured_title;
            break;
        case FILE_NOT_FOUND:
            mAuthStatusText = R.string.auth_incorrect_path_title;
            break;
        case OAUTH2_ERROR:
            mAuthStatusText = R.string.auth_oauth_error;
            break;
        case OAUTH2_ERROR_ACCESS_DENIED:
            mAuthStatusText = R.string.auth_oauth_error_access_denied;
            break;
        case UNHANDLED_HTTP_CODE:
        case UNKNOWN_ERROR:
            mAuthStatusText = R.string.auth_unknown_error_title;
            break;
        default:
            mAuthStatusText = 0;
            mAuthStatusIcon = 0;
        }
    }


    /**
     * Processes the result of the request for and access token send 
     * to an OAuth authorization server.
     * 
     * @param operation     Operation performed requesting the access token.
     * @param result        Result of the operation.
     */
    private void onGetOAuthAccessTokenFinish(OAuth2GetAccessToken operation, RemoteOperationResult result) {
        try {
            dismissDialog(DIALOG_OAUTH2_LOGIN_PROGRESS);
        } catch (IllegalArgumentException e) {
            // NOTHING TO DO ; can't find out what situation that leads to the exception in this code, but user logs signal that it happens
        }

        String webdav_path = AccountUtils.getWebdavPath(mDiscoveredVersion, true);
        if (result.isSuccess() && webdav_path != null) {
            /// be gentle with the user
            showDialog(DIALOG_LOGIN_PROGRESS);

            /// time to test the retrieved access token on the ownCloud server
            mOAuthAccessToken = ((OAuth2GetAccessToken)operation).getResultTokenMap().get(OAuth2Constants.KEY_ACCESS_TOKEN);
            Log_OC.d(TAG, "Got ACCESS TOKEN: " + mOAuthAccessToken);
            mAuthCheckOperation = new ExistenceCheckOperation("", this, false);
            WebdavClient client = OwnCloudClientUtils.createOwnCloudClient(Uri.parse(mHostBaseUrl + webdav_path), this);
            client.setBearerCredentials(mOAuthAccessToken);
            mAuthCheckOperation.execute(client, this, mHandler);

        } else {
            updateAuthStatusIconAndText(result);
            showAuthStatus();
            Log_OC.d(TAG, "Access failed: " + result.getLogMessage());
        }
    }


    /**
     * Processes the result of the access check performed to try the user credentials.
     * 
     * Creates a new account through the AccountManager.
     * 
     * @param operation     Access check performed.
     * @param result        Result of the operation.
     */
    private void onAuthorizationCheckFinish(ExistenceCheckOperation operation, RemoteOperationResult result) {
        try {
            dismissDialog(DIALOG_LOGIN_PROGRESS);
        } catch (IllegalArgumentException e) {
            // NOTHING TO DO ; can't find out what situation that leads to the exception in this code, but user logs signal that it happens
        }

        if (result.isSuccess()) {
            Log_OC.d(TAG, "Successful access - time to save the account");

            if (mAction == ACTION_CREATE) {
                createAccount();

            } else {
                updateToken();
            }

            finish();

        } else if (result.isServerFail() || result.isException()) {
            /// if server fail or exception in authorization, the UI is updated as when a server check failed
            mServerIsChecked = true;
            mServerIsValid = false;
            mIsSslConn = false;
            mOcServerChkOperation = null;
            mDiscoveredVersion = null;
            mHostBaseUrl = normalizeUrl(mHostUrlInput.getText().toString());

            // update status icon and text
            updateServerStatusIconAndText(result);
            showServerStatus();
            mAuthStatusIcon = 0;
            mAuthStatusText = 0;
            showAuthStatus();
            
            // update input controls state
            showRefreshButton();
            mOkButton.setEnabled(false);

            // very special case (TODO: move to a common place for all the remote operations) (dangerous here?)
            if (result.getCode() == ResultCode.SSL_RECOVERABLE_PEER_UNVERIFIED) {
                mLastSslUntrustedServerResult = result;
                showDialog(DIALOG_SSL_VALIDATOR); 
            }

        } else {    // authorization fail due to client side - probably wrong credentials
            updateAuthStatusIconAndText(result);
            showAuthStatus();
            Log_OC.d(TAG, "Access failed: " + result.getLogMessage());
        }
    }


    /**
     * Sets the proper response to get that the Account Authenticator that started this activity saves 
     * a new authorization token for mAccount.
     */
    private void updateToken() {
        Bundle response = new Bundle();
        response.putString(AccountManager.KEY_ACCOUNT_NAME, mAccount.name);
        response.putString(AccountManager.KEY_ACCOUNT_TYPE, mAccount.type);
        boolean isOAuth = mOAuth2Check.isChecked();
        if (isOAuth) {
            response.putString(AccountManager.KEY_AUTHTOKEN, mOAuthAccessToken);
            // the next line is necessary; by now, notifications are calling directly to the AuthenticatorActivity to update, without AccountManager intervention
            mAccountMgr.setAuthToken(mAccount, AccountAuthenticator.AUTH_TOKEN_TYPE_ACCESS_TOKEN, mOAuthAccessToken);
        } else {
            response.putString(AccountManager.KEY_AUTHTOKEN, mPasswordInput.getText().toString());
            mAccountMgr.setPassword(mAccount, mPasswordInput.getText().toString());
        }
        setAccountAuthenticatorResult(response);
    }


    /**
     * Creates a new account through the Account Authenticator that started this activity. 
     * 
     * This makes the account permanent.
     * 
     * TODO Decide how to name the OAuth accounts
     */
    private void createAccount() {
        /// create and save new ownCloud account
        boolean isOAuth = mOAuth2Check.isChecked();

        Uri uri = Uri.parse(mHostBaseUrl);
        String username = mUsernameInput.getText().toString().trim();
        if (isOAuth) {
            username = "OAuth_user" + (new java.util.Random(System.currentTimeMillis())).nextLong();
        }            
        String accountName = username + "@" + uri.getHost();
        if (uri.getPort() >= 0) {
            accountName += ":" + uri.getPort();
        }
        mAccount = new Account(accountName, AccountAuthenticator.ACCOUNT_TYPE);
        if (isOAuth) {
            mAccountMgr.addAccountExplicitly(mAccount, "", null);  // with our implementation, the password is never input in the app
        } else {
            mAccountMgr.addAccountExplicitly(mAccount, mPasswordInput.getText().toString(), null);
        }

        /// add the new account as default in preferences, if there is none already
        Account defaultAccount = AccountUtils.getCurrentOwnCloudAccount(this);
        if (defaultAccount == null) {
            SharedPreferences.Editor editor = PreferenceManager
                    .getDefaultSharedPreferences(this).edit();
            editor.putString("select_oc_account", accountName);
            editor.commit();
        }

        /// prepare result to return to the Authenticator
        //  TODO check again what the Authenticator makes with it; probably has the same effect as addAccountExplicitly, but it's not well done
        final Intent intent = new Intent();       
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE,    AccountAuthenticator.ACCOUNT_TYPE);
        intent.putExtra(AccountManager.KEY_ACCOUNT_NAME,    mAccount.name);
        if (!isOAuth)
            intent.putExtra(AccountManager.KEY_AUTHTOKEN,   AccountAuthenticator.ACCOUNT_TYPE); // TODO check this; not sure it's right; maybe
        intent.putExtra(AccountManager.KEY_USERDATA,        username);
        if (isOAuth) {
            mAccountMgr.setAuthToken(mAccount, AccountAuthenticator.AUTH_TOKEN_TYPE_ACCESS_TOKEN, mOAuthAccessToken);
        }
        /// add user data to the new account; TODO probably can be done in the last parameter addAccountExplicitly, or in KEY_USERDATA
        mAccountMgr.setUserData(mAccount, AccountAuthenticator.KEY_OC_VERSION,    mDiscoveredVersion.toString());
        mAccountMgr.setUserData(mAccount, AccountAuthenticator.KEY_OC_BASE_URL,   mHostBaseUrl);
        if (isOAuth)
            mAccountMgr.setUserData(mAccount, AccountAuthenticator.KEY_SUPPORTS_OAUTH2, "TRUE");  // TODO this flag should be unnecessary

        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);

        /// immediately request for the synchronization of the new account
        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        ContentResolver.requestSync(mAccount, AccountAuthenticator.AUTHORITY, bundle);
    }


    /**
     * {@inheritDoc}
     * 
     * Necessary to update the contents of the SSL Dialog
     * 
     * TODO move to some common place for all possible untrusted SSL failures
     */
    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        switch (id) {
        case DIALOG_LOGIN_PROGRESS:
        case DIALOG_CERT_NOT_SAVED:
        case DIALOG_OAUTH2_LOGIN_PROGRESS:
            break;
        case DIALOG_SSL_VALIDATOR: {
            ((SslValidatorDialog)dialog).updateResult(mLastSslUntrustedServerResult);
            break;
        }
        default:
            Log_OC.e(TAG, "Incorrect dialog called with id = " + id);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;
        switch (id) {
        case DIALOG_LOGIN_PROGRESS: {
            /// simple progress dialog
            ProgressDialog working_dialog = new ProgressDialog(this);
            working_dialog.setMessage(getResources().getString(R.string.auth_trying_to_login));
            working_dialog.setIndeterminate(true);
            working_dialog.setCancelable(true);
            working_dialog
            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    /// TODO study if this is enough
                    Log_OC.i(TAG, "Login canceled");
                    if (mOperationThread != null) {
                        mOperationThread.interrupt();
                        finish();
                    }
                }
            });
            dialog = working_dialog;
            break;
        }
        case DIALOG_OAUTH2_LOGIN_PROGRESS: {
            ProgressDialog working_dialog = new ProgressDialog(this);
            working_dialog.setMessage(String.format("Getting authorization")); 
            working_dialog.setIndeterminate(true);
            working_dialog.setCancelable(true);
            working_dialog
            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    Log_OC.i(TAG, "Login canceled");
                    finish();
                }
            });
            dialog = working_dialog;
            break;
        }
        case DIALOG_SSL_VALIDATOR: {
            /// TODO start to use new dialog interface, at least for this (it is a FragmentDialog already)
            dialog = SslValidatorDialog.newInstance(this, mLastSslUntrustedServerResult, this);
            break;
        }
        case DIALOG_CERT_NOT_SAVED: {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(getResources().getString(R.string.ssl_validator_not_saved));
            builder.setCancelable(false);
            builder.setPositiveButton(R.string.common_ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                };
            });
            dialog = builder.create();
            break;
        }
        default:
            Log_OC.e(TAG, "Incorrect dialog called with id = " + id);
        }
        return dialog;
    }


    /**
     * Starts and activity to open the 'new account' page in the ownCloud web site
     * 
     * @param view      'Account register' button
     */
    public void onRegisterClick(View view) {
        Intent register = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_account_register)));
        setResult(RESULT_CANCELED);
        startActivity(register);
    }


    /**
     * Updates the content and visibility state of the icon and text associated
     * to the last check on the ownCloud server.
     */
    private void showServerStatus() {
        TextView tv = (TextView) findViewById(R.id.server_status_text);

        if (mServerStatusIcon == 0 && mServerStatusText == 0) {
            tv.setVisibility(View.INVISIBLE);

        } else {
            tv.setText(mServerStatusText);
            tv.setCompoundDrawablesWithIntrinsicBounds(mServerStatusIcon, 0, 0, 0);
            tv.setVisibility(View.VISIBLE);
        }

    }


    /**
     * Updates the content and visibility state of the icon and text associated
     * to the interactions with the OAuth authorization server.
     */
    private void showAuthStatus() {
        if (mAuthStatusIcon == 0 && mAuthStatusText == 0) {
            mAuthStatusLayout.setVisibility(View.INVISIBLE);

        } else {
            mAuthStatusLayout.setText(mAuthStatusText);
            mAuthStatusLayout.setCompoundDrawablesWithIntrinsicBounds(mAuthStatusIcon, 0, 0, 0);
            mAuthStatusLayout.setVisibility(View.VISIBLE);
        }
    }     


    private void showRefreshButton() {
        mRefreshButton.setVisibility(View.VISIBLE);
    }

    private void hideRefreshButton() {
        mRefreshButton.setVisibility(View.GONE);
    }

    /**
     * Called when the refresh button in the input field for ownCloud host is clicked.
     * 
     * Performs a new check on the URL in the input field.
     * 
     * @param view      Refresh 'button'
     */
    public void onRefreshClick(View view) {
        checkOcServer();
    }
    
    
    /**
     * Called when the eye icon in the password field is clicked.
     * 
     * Toggles the visibility of the password in the field. 
     */
    public void onViewPasswordClick() {
        int selectionStart = mPasswordInput.getSelectionStart();
        int selectionEnd = mPasswordInput.getSelectionEnd();
        if (isPasswordVisible()) {
            hidePassword();
        } else {
            showPassword();
        }
        mPasswordInput.setSelection(selectionStart, selectionEnd);
    }    


    /**
     * Called when the checkbox for OAuth authorization is clicked.
     * 
     * Hides or shows the input fields for user & password. 
     * 
     * @param view      'View password' 'button'
     */
    public void onCheckClick(View view) {
        CheckBox oAuth2Check = (CheckBox)view;      
        changeViewByOAuth2Check(oAuth2Check.isChecked());

    }

    /**
     * Changes the visibility of input elements depending upon the kind of authorization
     * chosen by the user: basic or OAuth
     * 
     * @param checked       'True' when OAuth is selected.
     */
    public void changeViewByOAuth2Check(Boolean checked) {

        if (checked) {
            mOAuthAuthEndpointText.setVisibility(View.VISIBLE);
            mOAuthTokenEndpointText.setVisibility(View.VISIBLE);
            mUsernameInput.setVisibility(View.GONE);
            mPasswordInput.setVisibility(View.GONE);
        } else {
            mOAuthAuthEndpointText.setVisibility(View.GONE);
            mOAuthTokenEndpointText.setVisibility(View.GONE);
            mUsernameInput.setVisibility(View.VISIBLE);
            mPasswordInput.setVisibility(View.VISIBLE);
        }     

    }    

    /**
     * Called from SslValidatorDialog when a new server certificate was correctly saved.
     */
    public void onSavedCertificate() {
        checkOcServer();
    }

    /**
     * Called from SslValidatorDialog when a new server certificate could not be saved 
     * when the user requested it.
     */
    @Override
    public void onFailedSavingCertificate() {
        showDialog(DIALOG_CERT_NOT_SAVED);
    }


    /**
     *  Called when the 'action' button in an IME is pressed ('enter' in software keyboard).
     * 
     *  Used to trigger the authorization check when the user presses 'enter' after writing the password.
     */
    @Override
    public boolean onEditorAction(TextView inputField, int actionId, KeyEvent event) {
        if (inputField != null && inputField.equals(mPasswordInput) && 
                actionId == EditorInfo.IME_ACTION_DONE) {
            if (mOkButton.isEnabled()) {
                mOkButton.performClick();
            }
        }
        return false;   // always return false to grant that the software keyboard is hidden anyway
    }


    private abstract static class RightDrawableOnTouchListener implements OnTouchListener  {

        private int fuzz = 75;
        
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            Drawable rightDrawable = null;
            if (view instanceof TextView) {
                Drawable[] drawables = ((TextView)view).getCompoundDrawables();
                if (drawables.length > 2) {
                    rightDrawable = drawables[2];
                }
            }
            if (rightDrawable != null) {
                final int x = (int) event.getX();
                final int y = (int) event.getY();
                final Rect bounds = rightDrawable.getBounds();
                if (x >= (view.getRight() - bounds.width() - fuzz) && x <= (view.getRight() - view.getPaddingRight() + fuzz)
                    && y >= (view.getPaddingTop() - fuzz) && y <= (view.getHeight() - view.getPaddingBottom()) + fuzz) {
                    
                    return onDrawableTouch(event);
                }
            }
            return false;
        }

        public abstract boolean onDrawableTouch(final MotionEvent event);
    }

}
