package com.owncloud.android;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.util.Log;

/**
 * Log utilities, define different level of log, store the log into file
 * and name it according to the time. 
 * @author OwnCloud
 * @editor Shiyao Qi
 * @date 2013.12.21
 * @email qishiyao2008@126.com
 */

public class Log_OC {
    

    private static boolean isEnabled = false;
    private static File logFile; // The log file.
    private static File folder; // The log file folder.
    private static BufferedWriter buf; // BufferedWriter to write to file.
    
    public static void i(String TAG, String message){
        // Printing the message to LogCat console
        Log.i(TAG, message);
        // Write the log message to the file
        appendLog(TAG+" : "+message);
    }

    public static void d(String TAG, String message){
        Log.d(TAG, message);
        appendLog(TAG+" : "+message);
    }
    
    public static void d(String TAG, String message, Exception e) {
        Log.d(TAG, message, e);
        appendLog(TAG+" : "+ message+" Exception : "+e.getStackTrace());
    }
    
    
    public static void e(String TAG, String message){
        Log.e(TAG, message);
        appendLog(TAG+" : "+message);
    }
    
    public static void e(String TAG, String message, Throwable e) {
        Log.e(TAG, message, e);
        appendLog(TAG+" : "+ message+" Exception : "+e.getStackTrace());
    }
    
    public static void v(String TAG, String message){
        Log.v(TAG, message);
        appendLog(TAG+" : "+message);
    }
    
    public static void w(String TAG, String message) {
        Log.w(TAG,message); 
        appendLog(TAG+" : "+message);
    }
    
    public static void wtf(String TAG, String message) {
        Log.wtf(TAG,message); 
        appendLog(TAG+" : "+message);
    }
    
    /**
     * Start logging
     * @param logPath The log file folder.
     */
    public static void startLogging(String logPath) {
        folder = new File(logPath); // The log file folder.
        logFile = new File(folder + File.separator+"log.txt"); // The log file.
        
        if (!folder.exists()) { // If the log file folder is not exist, then create it.
            folder.mkdirs();
        }
        if (logFile.exists()) { // If the log file is exist, then delete it.
            logFile.delete();
        }
        try { 
            logFile.createNewFile(); // Create a file to store the log.
            buf = new BufferedWriter(new FileWriter(logFile, true)); // Write to the log file with appending.
            isEnabled = true; // Enable the log.
            appendPhoneInfo(); // Append the phone information.
        }catch (IOException e){ 
            e.printStackTrace(); 
        } 
    }
    
    /**
     * Stop logging.
     */
    public static void stopLogging() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.getDefault()); // Construct a new SimpleDateFormat.
        String currentDateandTime = sdf.format(new Date()); // Format current time to the defined format.
        if (logFile != null) { // If there are some log, rename the log file.
            logFile.renameTo(new File(folder+File.separator+"Owncloud_"+currentDateandTime+".log"));
          
            isEnabled = false; // Disable the logging.
            try {
                buf.close(); // Close the BufferedWriter.
            } catch (IOException e) {
                e.printStackTrace();
            } 
        
        }
        
    }
    
    /**
     * Append phone information to the BufferedWriter.
     */
    private static void appendPhoneInfo() {
        appendLog("Model : " + android.os.Build.MODEL);
        appendLog("Brand : " + android.os.Build.BRAND);
        appendLog("Product : " + android.os.Build.PRODUCT);
        appendLog("Device : " + android.os.Build.DEVICE);
        appendLog("Version-Codename : " + android.os.Build.VERSION.CODENAME); // Android Version Codename like GingerBread
        appendLog("Version-Release : " + android.os.Build.VERSION.RELEASE); // Android Version Release like 2.3.3
    }
    
    /**
     * Append text to the BufferedWrite.
     * @param text
     */
    private static void appendLog(String text) { 
        if (isEnabled) { // The log is enabled.
           try { 
               buf.append(text); // Append the text to BufferedWriter buf.
               buf.newLine(); // Write a new line to the buf.
           } catch (IOException e) { 
               e.printStackTrace(); 
           } 
        }
    }
}
