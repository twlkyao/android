/* ownCloud Android client application
 *   Copyright (C) 2012 Bartek Przybylski
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

package com.owncloud.android.operations;

import org.apache.http.HttpStatus;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;

import com.owncloud.android.Log_OC;
import com.owncloud.android.datamodel.DataStorageManager;
import com.owncloud.android.datamodel.OCFile;
import com.owncloud.android.files.services.FileDownloader;
import com.owncloud.android.files.services.FileUploader;
import com.owncloud.android.operations.RemoteOperationResult.ResultCode;

import eu.alefzero.webdav.WebdavClient;
import eu.alefzero.webdav.WebdavEntry;
import eu.alefzero.webdav.WebdavUtils;

public class SynchronizeFileOperation extends RemoteOperation {

    private String TAG = SynchronizeFileOperation.class.getSimpleName();
    private static final int SYNC_READ_TIMEOUT = 10000;
    private static final int SYNC_CONNECTION_TIMEOUT = 5000;
    
    private OCFile mLocalFile;
    private OCFile mServerFile;
    private DataStorageManager mStorageManager;
    private Account mAccount;
    private boolean mSyncFileContents;
    private boolean mLocalChangeAlreadyKnown;
    private Context mContext;
    
    private boolean mTransferWasRequested = false;
    
    public SynchronizeFileOperation(
            OCFile localFile,
            OCFile serverFile,          // make this null to let the operation checks the server; added to reuse info from SynchronizeFolderOperation 
            DataStorageManager storageManager, 
            Account account, 
            boolean syncFileContents,
            boolean localChangeAlreadyKnown, 
            Context context) {
        
        mLocalFile = localFile;
        mServerFile = serverFile;
        mStorageManager = storageManager;
        mAccount = account;
        mSyncFileContents = syncFileContents;
        mLocalChangeAlreadyKnown = localChangeAlreadyKnown;
        mContext = context;
    }


    @Override
    protected RemoteOperationResult run(WebdavClient client) {
        
        PropFindMethod propfind = null;
        RemoteOperationResult result = null;
        mTransferWasRequested = false;
        try {
            if (!mLocalFile.isDown()) {
                /// easy decision
                requestForDownload(mLocalFile);
                result = new RemoteOperationResult(ResultCode.OK);
                
            } else {
                /// local copy in the device -> need to think a bit more before do anything
                
                if (mServerFile == null) {
                    /// take the duty of check the server for the current state of the file there
                    propfind = new PropFindMethod(client.getBaseUri() + WebdavUtils.encodePath(mLocalFile.getRemotePath()));
                    int status = client.executeMethod(propfind, SYNC_READ_TIMEOUT, SYNC_CONNECTION_TIMEOUT);
                    boolean isMultiStatus = status == HttpStatus.SC_MULTI_STATUS;
                    if (isMultiStatus) {
                        MultiStatus resp = propfind.getResponseBodyAsMultiStatus();
                        WebdavEntry we = new WebdavEntry(resp.getResponses()[0],
                                               client.getBaseUri().getPath());
                        mServerFile = fillOCFile(we);
                        mServerFile.setLastSyncDateForProperties(System.currentTimeMillis());
                        
                    } else {
                        client.exhaustResponse(propfind.getResponseBodyAsStream());
                        result = new RemoteOperationResult(false, status);
                    }
                }
                
                if (result == null) {   // true if the server was not checked. nothing was wrong with the remote request
              
                    /// check changes in server and local file
                    boolean serverChanged = false;
                    if (mServerFile.getEtag() != null) {
                        serverChanged = (!mServerFile.getEtag().equals(mLocalFile.getEtag()));   // TODO could this be dangerous when the user upgrades the server from non-tagged to tagged?
                    } else {
                        // server without etags
                        serverChanged = (mServerFile.getModificationTimestamp() > mLocalFile.getModificationTimestampAtLastSyncForData());
                    }
                    boolean localChanged = (mLocalChangeAlreadyKnown || mLocalFile.getLocalModificationTimestamp() > mLocalFile.getLastSyncDateForData());
                        // TODO this will be always true after the app is upgraded to database version 2; will result in unnecessary uploads
              
                    /// decide action to perform depending upon changes
                    if (localChanged && serverChanged) {
                        result = new RemoteOperationResult(ResultCode.SYNC_CONFLICT);
                  
                    } else if (localChanged) {
                        if (mSyncFileContents) {
                            requestForUpload(mLocalFile);
                            // the local update of file properties will be done by the FileUploader service when the upload finishes
                        } else {
                            // NOTHING TO DO HERE: updating the properties of the file in the server without uploading the contents would be stupid; 
                            // So, an instance of SynchronizeFileOperation created with syncFileContents == false is completely useless when we suspect
                            // that an upload is necessary (for instance, in FileObserverService).
                        }
                        result = new RemoteOperationResult(ResultCode.OK);
                  
                    } else if (serverChanged) {
                        if (mSyncFileContents) {
                            requestForDownload(mLocalFile); // local, not server; we won't to keep the value of keepInSync!
                            // the update of local data will be done later by the FileUploader service when the upload finishes
                        } else {
                            // TODO CHECK: is this really useful in some point in the code?
                            mServerFile.setKeepInSync(mLocalFile.keepInSync());
                            mServerFile.setLastSyncDateForData(mLocalFile.getLastSyncDateForData());
                            mServerFile.setStoragePath(mLocalFile.getStoragePath());
                            mServerFile.setParentId(mLocalFile.getParentId());
                            mStorageManager.saveFile(mServerFile);
                            
                        }
                        result = new RemoteOperationResult(ResultCode.OK);
              
                    } else {
                        // nothing changed, nothing to do
                        result = new RemoteOperationResult(ResultCode.OK);
                    }
              
                } 
          
            }
            
            Log_OC.i(TAG, "Synchronizing " + mAccount.name + ", file " + mLocalFile.getRemotePath() + ": " + result.getLogMessage());
          
        } catch (Exception e) {
            result = new RemoteOperationResult(e);
            Log_OC.e(TAG, "Synchronizing " + mAccount.name + ", file " + (mLocalFile != null ? mLocalFile.getRemotePath() : "NULL") + ": " + result.getLogMessage(), result.getException());

        } finally {
            if (propfind != null)
                propfind.releaseConnection();
        }
        return result;
    }

    
    /**
     * Requests for an upload to the FileUploader service
     * 
     * @param file     OCFile object representing the file to upload
     */
    private void requestForUpload(OCFile file) {
        Intent i = new Intent(mContext, FileUploader.class);
        i.putExtra(FileUploader.KEY_ACCOUNT, mAccount);
        i.putExtra(FileUploader.KEY_FILE, file);
        /*i.putExtra(FileUploader.KEY_REMOTE_FILE, mRemotePath);    // doing this we would lose the value of keepInSync in the road, and maybe it's not updated in the database when the FileUploader service gets it!  
        i.putExtra(FileUploader.KEY_LOCAL_FILE, localFile.getStoragePath());*/
        i.putExtra(FileUploader.KEY_UPLOAD_TYPE, FileUploader.UPLOAD_SINGLE_FILE);
        i.putExtra(FileUploader.KEY_FORCE_OVERWRITE, true);
        mContext.startService(i);
        mTransferWasRequested = true;
    }


    /**
     * Requests for a download to the FileDownloader service
     * 
     * @param file     OCFile object representing the file to download
     */
    private void requestForDownload(OCFile file) {
        Intent i = new Intent(mContext, FileDownloader.class);
        i.putExtra(FileDownloader.EXTRA_ACCOUNT, mAccount);
        i.putExtra(FileDownloader.EXTRA_FILE, file);
        mContext.startService(i);
        mTransferWasRequested = true;
    }


    /**
     * Creates and populates a new {@link OCFile} object with the data read from the server.
     * 
     * @param we        WebDAV entry read from the server for a WebDAV resource (remote file or folder).
     * @return          New OCFile instance representing the remote resource described by we.
     */
    private OCFile fillOCFile(WebdavEntry we) {
        OCFile file = new OCFile(we.decodedPath());
        file.setCreationTimestamp(we.createTimestamp());
        file.setFileLength(we.contentLength());
        file.setMimetype(we.contentType());
        file.setModificationTimestamp(we.modifiedTimestamp());
        return file;
    }


    public boolean transferWasRequested() {
        return mTransferWasRequested;
    }


    public OCFile getLocalFile() {
        return mLocalFile;
    }

}
