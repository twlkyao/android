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

package com.owncloud.android;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.Vector;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore.Audio;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.owncloud.android.authentication.AccountAuthenticator;
import com.owncloud.android.datamodel.DataStorageManager;
import com.owncloud.android.datamodel.FileDataStorageManager;
import com.owncloud.android.datamodel.OCFile;
import com.owncloud.android.files.services.FileUploader;

import com.owncloud.android.R;

/**
 * This can be used to upload things to an ownCloud instance.
 * 
 * @author Bartek Przybylski
 * @editor Shiyao Qi
 * @date 2013.12.21
 * @email qishiyao2008@126.com
 */
public class Uploader extends ListActivity implements OnItemClickListener, android.view.View.OnClickListener {
    private static final String TAG = "ownCloudUploader"; // Tag string.

    private Account mAccount; // The user account.
    private AccountManager mAccountManager; // AccountManger.
    private Stack<String> mParents; // To store the full file path.
    private ArrayList<Parcelable> mStreamsToUpload; // To store the file to be uploaded.
    private boolean mCreateDir; // Indicate whether to create a directory.
    private String mUploadPath; // The file upload file path.
    private DataStorageManager mStorageManager; // The DataStorageManager interface.
    private OCFile mFile; // OCFile.

    private final static int DIALOG_NO_ACCOUNT = 0; // Indicator for no account.
    private final static int DIALOG_WAITING = 1; // Indicator for waiting.
    private final static int DIALOG_NO_STREAM = 2; // Indicator for no stream.
    private final static int DIALOG_MULTIPLE_ACCOUNT = 3; // Indicator for multiple account.

    private final static int REQUEST_CODE_SETUP_ACCOUNT = 0; // Request code.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_NO_TITLE); // Disable the title bar.
        mParents = new Stack<String>();
        mParents.add("");
        if (prepareStreamsToUpload()) { // The preparation is ready.
            mAccountManager = (AccountManager) getSystemService(Context.ACCOUNT_SERVICE);
            Account[] accounts = mAccountManager.getAccountsByType(AccountAuthenticator.ACCOUNT_TYPE);
            if (accounts.length == 0) { // There are no accounts.
                Log_OC.i(TAG, "No ownCloud account is available");
                showDialog(DIALOG_NO_ACCOUNT); 
            } else if (accounts.length > 1) { // There are more than one account.
                Log_OC.i(TAG, "More then one ownCloud is available");
                showDialog(DIALOG_MULTIPLE_ACCOUNT);
            } else { // There is only one account.
                mAccount = accounts[0]; // Initialize the mAccount.
                mStorageManager = new FileDataStorageManager(mAccount, getContentResolver());
                // Call the function to show all the file directorys under the constructed full file path.
                populateDirectoryList(); 
            }
        } else {
            showDialog(DIALOG_NO_STREAM);
        }
    }
    
    /**
     * Construct different dialog according to the id.
     */
    @Override
    protected Dialog onCreateDialog(final int id) {
        final AlertDialog.Builder builder = new Builder(this);
        switch (id) {
        case DIALOG_WAITING: // The file is uploading.
            ProgressDialog pDialog = new ProgressDialog(this);
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(false);
            pDialog.setMessage(getResources().getString(R.string.uploader_info_uploading));
            return pDialog;
        case DIALOG_NO_ACCOUNT: // There are no account, then setup an account.
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setTitle(R.string.uploader_wrn_no_account_title);
            builder.setMessage(String.format(getString(R.string.uploader_wrn_no_account_text),
                    getString(R.string.app_name)));
            builder.setCancelable(false);
            builder.setPositiveButton(R.string.uploader_wrn_no_account_setup_btn_text, new OnClickListener() {
            
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.ECLAIR_MR1) {
                        // using string value since in API7 this
                        // constatn is not defined
                        // in API7 < this constatant is defined in
                        // Settings.ADD_ACCOUNT_SETTINGS
                        // and Settings.EXTRA_AUTHORITIES
                        Intent intent = new Intent(android.provider.Settings.ACTION_ADD_ACCOUNT);
                        intent.putExtra("authorities", new String[] { AccountAuthenticator.AUTHORITY });
                        startActivityForResult(intent, REQUEST_CODE_SETUP_ACCOUNT); // Start activity with request code.
                    } else { // If the API level is not higher than 7, use self-defined AccountAuthenticaotor.
                        // since in API7 there is no direct call for
                        // account setup, so we need to
                        // show our own AccountSetupAcricity, get
                        // desired results and setup
                        // everything for ourself
                        Intent intent = new Intent(getBaseContext(), AccountAuthenticator.class);
                        startActivityForResult(intent, REQUEST_CODE_SETUP_ACCOUNT);
                    }
                }
            });
            builder.setNegativeButton(R.string.uploader_wrn_no_account_quit_btn_text, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
            return builder.create();
        case DIALOG_MULTIPLE_ACCOUNT: // There are multimple accounts.
            CharSequence ac[] = new CharSequence[mAccountManager.getAccountsByType(AccountAuthenticator.ACCOUNT_TYPE).length];
            for (int i = 0; i < ac.length; ++i) { // Fill in the array with the account name.
                ac[i] = mAccountManager.getAccountsByType(AccountAuthenticator.ACCOUNT_TYPE)[i].name;
            }
            builder.setTitle(R.string.common_choose_account);
            builder.setItems(ac, new OnClickListener() { // Display all the available accounts.
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Get the clicked account.
                    mAccount = mAccountManager.getAccountsByType(AccountAuthenticator.ACCOUNT_TYPE)[which];
                    // Pass the account to the FileDataStorageManager.
                    mStorageManager = new FileDataStorageManager(mAccount, getContentResolver());
                    populateDirectoryList(); // Call the function to display the file folders under the constructed full file path.
                }
            });
            builder.setCancelable(true);
            builder.setOnCancelListener(new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    dialog.cancel();
                    finish();
                }
            });
            return builder.create();
        case DIALOG_NO_STREAM: // There are no content to upload.
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setTitle(R.string.uploader_wrn_no_content_title);
            builder.setMessage(R.string.uploader_wrn_no_content_text);
            builder.setCancelable(false);
            builder.setNegativeButton(R.string.common_cancel, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
            return builder.create();
        default:
            throw new IllegalArgumentException("Unknown dialog id: " + id);
        }
    }

    /**
     * Implement the OnClickListener for the full file path.
     */
    class a implements OnClickListener {
        String mPath; // Full file path.
        EditText mDirname; // The current file directory EditText.

        public a(String path, EditText dirname) {
            mPath = path; 
            mDirname = dirname;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            Uploader.this.mUploadPath = mPath + mDirname.getText().toString(); // Construct the full upload file path.
            Uploader.this.mCreateDir = true;
            uploadFiles(); // Call the function to upload file.
        }
    }

    /**
     * Back up to the parent directory if the mParents
     * contains more than one elements or exit when there
     * are no more than one element.
     */
    @Override
    public void onBackPressed() {

        if (mParents.size() <= 1) { // Finish the activity.
            super.onBackPressed();
            return;
        } else {
            mParents.pop(); // Pop the last directory
            populateDirectoryList(); // Refresh the file directory list.
        }
    }

    /**
     * Implement the file directory listview onItemClick().
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // Click on folder in the list
        Log_OC.d(TAG, "on item click");
        Vector<OCFile> tmpfiles = mStorageManager.getDirectoryContent(mFile);
        if (tmpfiles.size() <= 0) // There are no file directory.
            return;
        // filter on dirtype
        Vector<OCFile> files = new Vector<OCFile>();
        for (OCFile f : tmpfiles) { // Add all the file directory to the Vector files.
            if (f.isDirectory())
                files.add(f);
        }
           
        if (files.size() < position) {
            throw new IndexOutOfBoundsException("Incorrect item selected");
        }
        mParents.push(files.get(position).getFileName()); // Add the file directory name to the mParents.
        populateDirectoryList(); // Refresh the file directory list.
    }

    /**
     * Implement the onClick() for the upload button.
     */
    @Override
    public void onClick(View v) {
        // click on button
        switch (v.getId()) {
        case R.id.uploader_choose_folder:
            /** 
             * First element in mParents is root dir,
             * represented by ""; init mUploadPath with "/" results in a "//" prefix
             */
            mUploadPath = "";
            for (String p : mParents) // Construct the full file directory.
                mUploadPath += p + OCFile.PATH_SEPARATOR;
            Log_OC.d(TAG, "Uploading file to dir " + mUploadPath);

            uploadFiles(); // Call the function to upload file.
            break;
        default:
            throw new IllegalArgumentException("Wrong element clicked");
        }
    }

    /**
     * Do something according to the activity result.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log_OC.i(TAG, "result received. req: " + requestCode + " res: " + resultCode);
        if (requestCode == REQUEST_CODE_SETUP_ACCOUNT) {
            dismissDialog(DIALOG_NO_ACCOUNT);
            if (resultCode == RESULT_CANCELED) {
                finish();
            }
            Account[] accounts = mAccountManager.getAccountsByType(AccountAuthenticator.AUTH_TOKEN_TYPE);
            if (accounts.length == 0) { // There are no accounts.
                showDialog(DIALOG_NO_ACCOUNT);
            } else {
                // there is no need for checking for is there more than one
                // account at this point, since account setup can set only one account at time
                mAccount = accounts[0]; // Get the account.
                populateDirectoryList(); // Refresh the file directory list.
            }
        }
    }

    /**
     * Get the file directory under the constructed full file path.
     */
    private void populateDirectoryList() {
        setContentView(R.layout.uploader_layout);

        String full_path = ""; // The filepath.
        for (String a : mParents) // Construct the full file path.
            full_path += a + "/";
        
        Log_OC.d(TAG, "Populating view with content of : " + full_path);
        
        mFile = mStorageManager.getFileByPath(full_path); // Call the function to get file.
        if (mFile != null) {
            Vector<OCFile> files = mStorageManager.getDirectoryContent(mFile); // 
            List<HashMap<String, Object>> data = new LinkedList<HashMap<String,Object>>();
            for (OCFile f : files) { // Add all the file directory into the List data.
                HashMap<String, Object> h = new HashMap<String, Object>();
                if (f.isDirectory()) {
                    h.put("dirname", f.getFileName()); // Map the file directory name with dirname.
                    data.add(h);
                }
            }
            SimpleAdapter sa = new SimpleAdapter(this,data,
                    R.layout.uploader_list_item_layout,
                    new String[] {"dirname"},
                    new int[] {R.id.textView1});
            
            setListAdapter(sa);
            /**
             * Find the upload button and set listener.
             */
            Button btn = (Button) findViewById(R.id.uploader_choose_folder);
            btn.setOnClickListener(this);
            getListView().setOnItemClickListener(this);
        }
    }

    /**
     * According to the intent add the parcelable data to mStreamsToUpload.
     * @return The upload status.
     */
    private boolean prepareStreamsToUpload() {
        if (getIntent().getAction().equals(Intent.ACTION_SEND)) { // Upload file.
            mStreamsToUpload = new ArrayList<Parcelable>();
            mStreamsToUpload.add(getIntent().getParcelableExtra(Intent.EXTRA_STREAM)); // Add the parcelable data to mStreamsToUpload.
        } else if (getIntent().getAction().equals(Intent.ACTION_SEND_MULTIPLE)) { // Upload multiple files.
            mStreamsToUpload = getIntent().getParcelableArrayListExtra(Intent.EXTRA_STREAM); // Add the parcelable data to mStreamsToUpload.
        }
        return (mStreamsToUpload != null && mStreamsToUpload.get(0) != null);
    }

    /**
     * Upload file to the cloud.
     */
    public void uploadFiles() {
        try {
            //WebdavClient webdav = OwnCloudClientUtils.createOwnCloudClient(mAccount, getApplicationContext());

            ArrayList<String> local = new ArrayList<String>(); // Local file.
            ArrayList<String> remote = new ArrayList<String>(); // Remote file.
            
            /* TODO - mCreateDir can never be true at this moment; we will replace wdc.createDirectory by CreateFolderOperation when that is fixed 
            WebdavClient wdc = OwnCloudClientUtils.createOwnCloudClient(mAccount, getApplicationContext());
            // create last directory in path if necessary
            if (mCreateDir) {
                wdc.createDirectory(mUploadPath);
            }
            */
            
            // this checks the mimeType 
            for (Parcelable mStream : mStreamsToUpload) { // All Parcelable in mStreamToUpload.
                
                Uri uri = (Uri) mStream; // 
                if (uri !=null) {
                    if (uri.getScheme().equals("content")) { // The scheme of the uri is content.
                        
                        String mimeType = getContentResolver().getType(uri); // Get the mime type of the uri.
                        if (mimeType.contains("image")) { // Image.
                            String[] CONTENT_PROJECTION = { Images.Media.DATA, Images.Media.DISPLAY_NAME, Images.Media.MIME_TYPE, Images.Media.SIZE};
                            Cursor c = getContentResolver().query(uri, CONTENT_PROJECTION, null, null, null);
                            c.moveToFirst();
                            int index = c.getColumnIndex(Images.Media.DATA);
                            String data = c.getString(index);
                            local.add(data); // Local add the data.
                            remote.add(mUploadPath + c.getString(c.getColumnIndex(Images.Media.DISPLAY_NAME))); // Remote add the full file directroy path.
                       } else if (mimeType.contains("video")) { // Video.
                           String[] CONTENT_PROJECTION = { Video.Media.DATA, Video.Media.DISPLAY_NAME, Video.Media.MIME_TYPE, Video.Media.SIZE, Video.Media.DATE_MODIFIED };
                           Cursor c = getContentResolver().query(uri, CONTENT_PROJECTION, null, null, null);
                           c.moveToFirst();
                           int index = c.getColumnIndex(Video.Media.DATA);
                           String data = c.getString(index);
                           local.add(data);
                           remote.add(mUploadPath + c.getString(c.getColumnIndex(Video.Media.DISPLAY_NAME)));
                       } else if (mimeType.contains("audio")) { // Audio.
                           String[] CONTENT_PROJECTION = { Audio.Media.DATA, Audio.Media.DISPLAY_NAME, Audio.Media.MIME_TYPE, Audio.Media.SIZE };
                           Cursor c = getContentResolver().query(uri, CONTENT_PROJECTION, null, null, null);
                           c.moveToFirst();
                           int index = c.getColumnIndex(Audio.Media.DATA);
                           String data = c.getString(index);
                           local.add(data);
                           remote.add(mUploadPath + c.getString(c.getColumnIndex(Audio.Media.DISPLAY_NAME)));
                       } else { // Other content mime type.
                           String filePath = Uri.decode(uri.toString()).replace(uri.getScheme() + "://", "");
                           // cut everything whats before mnt. It occured to me that sometimes apps send their name into the URI
                           if (filePath.contains("mnt")) {
                              String splitedFilePath[] = filePath.split("/mnt");
                              filePath = splitedFilePath[1];
                           }
                           final File file = new File(filePath);
                           local.add(file.getAbsolutePath()); // Local add the data.
                           remote.add(mUploadPath + file.getName()); // Remote add the full file directroy path.
                       }
                    } else if (uri.getScheme().equals("file")) { // File.
                        String filePath = Uri.decode(uri.toString()).replace(uri.getScheme() + "://", "");
                        if (filePath.contains("mnt")) {
                           String splitedFilePath[] = filePath.split("/mnt");
                           filePath = splitedFilePath[1];
                        }
                        final File file = new File(filePath);
                        local.add(file.getAbsolutePath());
                        remote.add(mUploadPath + file.getName());
                    } else { // Other type.
                        throw new SecurityException();
                    }
                } else { // Uri is null.
                    throw new SecurityException();
            }
           
            /**
             * Start the file upload service.
             */
            Intent intent = new Intent(getApplicationContext(), FileUploader.class);
            intent.putExtra(FileUploader.KEY_UPLOAD_TYPE, FileUploader.UPLOAD_MULTIPLE_FILES);
            intent.putExtra(FileUploader.KEY_LOCAL_FILE, local.toArray(new String[local.size()]));
            intent.putExtra(FileUploader.KEY_REMOTE_FILE, remote.toArray(new String[remote.size()]));
            intent.putExtra(FileUploader.KEY_ACCOUNT, mAccount);
            startService(intent);
            finish();
            }
            
        } catch (SecurityException e) {
            String message = String.format(getString(R.string.uploader_error_forbidden_content),
                    getString(R.string.app_name));
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }
}
