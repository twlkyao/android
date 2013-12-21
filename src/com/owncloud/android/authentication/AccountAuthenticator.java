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

import android.accounts.*;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import com.owncloud.android.Log_OC;

/**
 *  Authenticator for ownCloud accounts.
 * 
 *  Controller class accessed from the system AccountManager,
 *  providing integration of ownCloud accounts with the Android system.
 * 
 *  TODO - better separation in operations for OAuth-capable and regular ownCloud accounts.
 *  TODO - review completeness 
 * 
 * @author David A. Velasco
 * @editor Shiyao Qi
 * @date 2013.12.21
 * @email qishiyao2008@126.com
 */
public class AccountAuthenticator extends AbstractAccountAuthenticator {
    
    /**
     * Is used by android system to assign accounts to authenticators.
     * Should be used by application and all extensions.
     */
    public static final String ACCOUNT_TYPE = "owncloud"; // Account type.
    public static final String AUTHORITY = "org.owncloud"; // Authority.
    public static final String AUTH_TOKEN_TYPE = "org.owncloud"; // Authority token type.
    public static final String AUTH_TOKEN_TYPE_PASSWORD = "owncloud.password"; // Authority token type password.
    public static final String AUTH_TOKEN_TYPE_ACCESS_TOKEN = "owncloud.oauth2.access_token"; // Authority token type access token.
    public static final String AUTH_TOKEN_TYPE_REFRESH_TOKEN = "owncloud.oauth2.refresh_token"; // Authority token type refresh token.

    public static final String KEY_AUTH_TOKEN_TYPE = "authTokenType"; // Key authorization token type.
    public static final String KEY_REQUIRED_FEATURES = "requiredFeatures"; // Key required features.
    public static final String KEY_LOGIN_OPTIONS = "loginOptions"; // Key login options.
    public static final String KEY_ACCOUNT = "account"; // Key account.
    
    /**
     * Value under this key should handle path to webdav php script.
     * Will be removed and usage should be replaced by combining
     * {@link com.owncloud.android.authentication.AuthenticatorActivity.KEY_OC_BASE_URL} and
     * {@link com.owncloud.android.utils.OwnCloudVersion}
     * 
     * @deprecated
     */
    public static final String KEY_OC_URL = "oc_url";
    /**
     * Version should be 3 numbers separated by dot so it can be parsed by
     * {@link com.owncloud.android.utils.OwnCloudVersion}
     */
    public static final String KEY_OC_VERSION = "oc_version";
    /**
     * Base url should point to owncloud installation without trailing "/" ie:
     * http://server/path or https://owncloud.server
     */
    public static final String KEY_OC_BASE_URL = "oc_base_url";
    /**
     * Flag signaling if the ownCloud server can be accessed with OAuth2 access tokens.
     */
    public static final String KEY_SUPPORTS_OAUTH2 = "oc_supports_oauth2";
    
    private static final String TAG = AccountAuthenticator.class.getSimpleName(); // The class name.
    
    private Context mContext;

    public AccountAuthenticator(Context context) {
        super(context);
        mContext = context;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response,
            String accountType, String authTokenType,
            String[] requiredFeatures, Bundle options)
            throws NetworkErrorException {
        
        Log_OC.i(TAG, "Adding account with type " + accountType
                + " and auth token " + authTokenType);
        try { // Validate the account type.
            validateAccountType(accountType);
        } catch (AuthenticatorException e) {
            Log_OC.e(TAG, "Failed to validate account type " + accountType + ": "
                    + e.getMessage());
            e.printStackTrace();
            return e.getFailureBundle();
        }
        
        final Intent intent = new Intent(mContext, AuthenticatorActivity.class);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        intent.putExtra(KEY_AUTH_TOKEN_TYPE, authTokenType);
        intent.putExtra(KEY_REQUIRED_FEATURES, requiredFeatures);
        intent.putExtra(KEY_LOGIN_OPTIONS, options);
        intent.putExtra(AuthenticatorActivity.EXTRA_ACTION, AuthenticatorActivity.ACTION_CREATE);

        setIntentFlags(intent);
        
        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response,
            Account account, Bundle options) throws NetworkErrorException {
        try {
            validateAccountType(account.type); // Validate the account type.
        } catch (AuthenticatorException e) {
            Log_OC.e(TAG, "Failed to validate account type " + account.type + ": "
                    + e.getMessage());
            e.printStackTrace();
            return e.getFailureBundle();
        }
        Intent intent = new Intent(mContext, AuthenticatorActivity.class);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE,
                response);
        intent.putExtra(KEY_ACCOUNT, account);
        intent.putExtra(KEY_LOGIN_OPTIONS, options);

        setIntentFlags(intent);

        Bundle resultBundle = new Bundle();
        resultBundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return resultBundle;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response,
            String accountType) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response,
            Account account, String authTokenType, Bundle options)
            throws NetworkErrorException {
        // validate parameters
        try {
            validateAccountType(account.type);
            validateAuthTokenType(authTokenType);
        } catch (AuthenticatorException e) {
            Log_OC.e(TAG, "Failed to validate account type " + account.type + ": "
                    + e.getMessage());
            e.printStackTrace();
            return e.getFailureBundle();
        }
        
        /// check if required token is stored
        final AccountManager am = AccountManager.get(mContext);
        String accessToken;
        if (authTokenType.equals(AUTH_TOKEN_TYPE_PASSWORD)) {
            accessToken = am.getPassword(account); // Get the password associated with account.
        } else {
            accessToken = am.peekAuthToken(account, authTokenType); // Get the cached authorization token type associated with account. 
        }
        if (accessToken != null) {
            final Bundle result = new Bundle();
            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, ACCOUNT_TYPE);
            result.putString(AccountManager.KEY_AUTHTOKEN, accessToken);
            return result;
        }
        
        /**
         *  if not stored, return Intent to access the AuthenticatorActivity
         *  and UPDATE the token for the account
        */
        final Intent intent = new Intent(mContext, AuthenticatorActivity.class);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        intent.putExtra(KEY_AUTH_TOKEN_TYPE, authTokenType);
        intent.putExtra(KEY_LOGIN_OPTIONS, options);
        intent.putExtra(AuthenticatorActivity.EXTRA_ACCOUNT, account);
        intent.putExtra(AuthenticatorActivity.EXTRA_ENFORCED_UPDATE, true);
        intent.putExtra(AuthenticatorActivity.EXTRA_ACTION, AuthenticatorActivity.ACTION_UPDATE_TOKEN);

        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        return null;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response,
            Account account, String[] features) throws NetworkErrorException {
        final Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
        return result;
    }

    /**
     * Update Credentials.
     */
    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response,
            Account account, String authTokenType, Bundle options)
            throws NetworkErrorException {
        final Intent intent = new Intent(mContext, AuthenticatorActivity.class);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE,
                response);
        intent.putExtra(KEY_ACCOUNT, account);
        intent.putExtra(KEY_AUTH_TOKEN_TYPE, authTokenType);
        intent.putExtra(KEY_LOGIN_OPTIONS, options);
        setIntentFlags(intent);

        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    @Override
    public Bundle getAccountRemovalAllowed(
            AccountAuthenticatorResponse response, Account account)
            throws NetworkErrorException {
        return super.getAccountRemovalAllowed(response, account);
    }

    /**
     * Set IntentFlags.
     * @param intent The intent to set flags.
     */
    private void setIntentFlags(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
    }

    /**
     * Validate the Account type.
     * @param type The type of the account.
     * @throws UnsupportedAccountTypeException
     */
    private void validateAccountType(String type)
            throws UnsupportedAccountTypeException {
        if (!type.equals(ACCOUNT_TYPE)) {
            throw new UnsupportedAccountTypeException();
        }
    }

    /**
     * Validate Auth Token type.
     * @param authTokenType
     * @throws UnsupportedAuthTokenTypeException
     */
    private void validateAuthTokenType(String authTokenType)
            throws UnsupportedAuthTokenTypeException {
        if (!authTokenType.equals(AUTH_TOKEN_TYPE) &&
            !authTokenType.equals(AUTH_TOKEN_TYPE_PASSWORD) &&
            !authTokenType.equals(AUTH_TOKEN_TYPE_ACCESS_TOKEN) &&
            !authTokenType.equals(AUTH_TOKEN_TYPE_REFRESH_TOKEN) ) {
            throw new UnsupportedAuthTokenTypeException();
        }
    }

    /**
     * AuthenticatorException.
     */
    public static class AuthenticatorException extends Exception {
        private static final long serialVersionUID = 1L; // SerivalVersionUID.
        private Bundle mFailureBundle; // 

        public AuthenticatorException(int code, String errorMsg) {
            mFailureBundle = new Bundle();
            mFailureBundle.putInt(AccountManager.KEY_ERROR_CODE, code);
            mFailureBundle.putString(AccountManager.KEY_ERROR_MESSAGE, errorMsg);
        }

        /**
         * Return mFailuerBundle.
         * @return mFailuerBundle.
         */
        public Bundle getFailureBundle() {
            return mFailureBundle;
        }
    }

    /**
     * UnsupportedAccountTypeException.
     */
    public static class UnsupportedAccountTypeException extends
            AuthenticatorException {
        private static final long serialVersionUID = 1L; // SerialVersionUID.

        public UnsupportedAccountTypeException() {
            super(AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION,
                    "Unsupported account type");
        }
    }

    /**
     * UnsupportedAuthTypeException.
     */
    public static class UnsupportedAuthTokenTypeException extends
            AuthenticatorException {
        private static final long serialVersionUID = 1L; // SerialVersionUID.

        public UnsupportedAuthTokenTypeException() {
            super(AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION,
                    "Unsupported auth token type");
        }
    }

    /**
     * UnsupportedFeaturesException.
     */
    public static class UnsupportedFeaturesException extends
            AuthenticatorException {
        public static final long serialVersionUID = 1L;

        public UnsupportedFeaturesException() {
            super(AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION,
                    "Unsupported features");
        }
    }

    /**
     * AccessDeniedException.
     */
    public static class AccessDeniedException extends AuthenticatorException {
        private static final long serialVersionUID = 1L;
        
        public AccessDeniedException(int code, String errorMsg) {
            super(AccountManager.ERROR_CODE_INVALID_RESPONSE, "Access Denied");
        }
    }
}
