
package com.aware;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.aware.providers.ESM_Provider;
import com.aware.providers.ESM_Provider.ESM_Data;
import com.aware.ui.ESM_Queue;
import com.aware.ui.PermissionsHandler;
import com.aware.ui.esms.ESMFactory;
import com.aware.ui.esms.ESM_Question;
import com.aware.utils.Aware_Sensor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * AWARE ESM module
 * Allows a researcher to do ESM's on their studies
 * Listens to:
 * - ACTION_AWARE_QUEUE_ESM
 *
 * @author df
 */
public class ESM extends Aware_Sensor {

    /**
     * Logging tag (default = "AWARE::ESM")
     */
    private static String TAG = "AWARE::ESM";

    /**
     * Received event: queue the specified ESM
     * Extras: (JSONArray as String) esm
     */
    public static final String ACTION_AWARE_QUEUE_ESM = "ACTION_AWARE_QUEUE_ESM";

    /**
     * Received event: try the specified ESM
     * Extras: (JSONArray as String) esm
     */
    public static final String ACTION_AWARE_TRY_ESM = "ACTION_AWARE_TRY_ESM";

    /**
     * Broadcasted event: the user has answered one answer from ESM queue
     */
    public static final String ACTION_AWARE_ESM_ANSWERED = "ACTION_AWARE_ESM_ANSWERED";

    /**
     * Broadcasted event: the user has dismissed one answer from ESM queue
     */
    public static final String ACTION_AWARE_ESM_DISMISSED = "ACTION_AWARE_ESM_DISMISSED";

    /**
     * Broadcasted event: the user did not answer the ESM on time from ESM queue
     */
    public static final String ACTION_AWARE_ESM_EXPIRED = "ACTION_AWARE_ESM_EXPIRED";

    /**
     * Broadcasted event: the user has finished answering the ESM queue
     */
    public static final String ACTION_AWARE_ESM_QUEUE_COMPLETE = "ACTION_AWARE_ESM_QUEUE_COMPLETE";

    /**
     * Broadcasted event: the user has started answering the ESM queue
     */
    public static final String ACTION_AWARE_ESM_QUEUE_STARTED = "ACTION_AWARE_ESM_QUEUE_STARTED";

    /**
     * ESM status: new on the queue, but not displayed yet
     */
    public static final int STATUS_NEW = 0;

    /**
     * ESM status: esm dismissed by the user, either by pressed back or home button
     */
    public static final int STATUS_DISMISSED = 1;

    /**
     * ESM status: esm answered by the user by pressing submit button
     */
    public static final int STATUS_ANSWERED = 2;

    /**
     * ESM status: esm not answered in time (esm visible and not answered, notification timeout)
     */
    public static final int STATUS_EXPIRED = 3;

    /**
     * ESM status: esm is visible to the user
     */
    public static final int STATUS_VISIBLE = 4;

    /**
     * ESM status: esm was not visible because of flow condition, branching to another esm
     */
    public static final int STATUS_BRANCHED = 5;

    /**
     * ESM Dialog with free text
     * Example: [{'esm':{'esm_type':1,'esm_title':'ESM Freetext','esm_instructions':'The user can answer an open ended question.','esm_submit':'Next','esm_expiration_threshold':20,'esm_trigger':'esm trigger example'}}]
     */
    public static final int TYPE_ESM_TEXT = 1;

    /**
     * ESM Dialog with radio buttons
     * Note: 'Other' will allow free text input from the user
     * Example: [{'esm':{'esm_type':2,'esm_title':'ESM Radio','esm_instructions':'The user can only choose one option','esm_radios':['Option one','Option two','Other'],'esm_submit':'Next','esm_expiration_threshold':30,'esm_trigger':'esm trigger example'}}]
     */
    public static final int TYPE_ESM_RADIO = 2;

    /**
     * ESM Dialog with checkboxes
     * Note: 'Other' will allow free text input from the user
     * Example: [{'esm':{'esm_type':3,'esm_title':'ESM Checkbox','esm_instructions':'The user can choose multiple options','esm_checkboxes':['One','Two','Other'],'esm_submit':'Next','esm_expiration_threshold':40,'esm_trigger':'esm trigger example'}}]
     */
    public static final int TYPE_ESM_CHECKBOX = 3;

    /**
     * ESM Dialog with likert scale
     * Example: [{'esm':{'esm_type':4,'esm_title':'ESM Likert','esm_instructions':'User rating 1 to 5 or 7 at 1 step increments','esm_likert_max':5,'esm_likert_max_label':'Great','esm_likert_min_label':'Bad','esm_likert_step':1,'esm_submit':'OK','esm_expiration_threshold':50,'esm_trigger':'esm trigger example'}}]
     */
    public static final int TYPE_ESM_LIKERT = 4;

    /**
     * ESM Dialog with quick answers
     * Example: [{'esm':{'esm_type':5,'esm_title':'ESM Quick Answer','esm_instructions':'One touch answer','esm_quick_answers':['Yes','No'],'esm_expiration_threashold':60,'esm_trigger':'esm trigger example'}}]
     */
    public static final int TYPE_ESM_QUICK_ANSWERS = 5;

    /**
     * ESM Dialog with a discrete likert scale
     * Example: [{'esm':{'esm_type':6,'esm_title':'ESM Scale','esm_instructions':'User scaled value between minimum and maximum at X increments','esm_scale_min':0,'esm_scale_max':5,'esm_scale_start':3,'esm_scale_max_label':'5','esm_scale_min_label':'0','esm_scale_step':1,'esm_submit':'OK','esm_expiration_threshold':50,'esm_trigger':'esm trigger example'}}]
     */
    public static final int TYPE_ESM_SCALE = 6;

    /**
     * ESM Dialog with a date and time picker
     * Example: [{"esm":{"esm_type":7,"esm_title":"Date and time","esm_instructions":"When did this happen?","esm_submit":"OK","esm_trigger":"AWARE Test"}}]
     */
    public static final int TYPE_ESM_DATETIME = 7;

    /**
     * ESM Dialog with PAM (Photographic Affect Meter)
     * [{"esm":{"esm_type":8,"esm_title":"PAM","esm_instructions":"Select what best illustrates your mood","esm_submit":"OK","esm_trigger":"AWARE Test"}}]
     */
    public static final int TYPE_ESM_PAM = 8;

    /**
     * ESM Dialog with number input only
     * Example: [{'esm':{'esm_type':9,'esm_title':'ESM Number','esm_instructions':'User can answer with any numeric value','esm_submit':'Next','esm_expiration_threshold':20,'esm_trigger':'esm trigger example'}}]
     */
    public static final int TYPE_ESM_NUMBER = 9;


    /**
     * Required String extra for displaying an ESM. It should contain the JSON string that defines the ESM dialog.
     * Examples:<p>
     * Free text: [{'esm':{'esm_type':1,'esm_title':'ESM Freetext','esm_instructions':'The user can answer an open ended question.','esm_submit':'Next','esm_expiration_threshold':20,'esm_trigger':'esm trigger example'}}]
     * Radio: [{'esm':{'esm_type':2,'esm_title':'ESM Radio','esm_instructions':'The user can only choose one option','esm_radios':['Option one','Option two','Other'],'esm_submit':'Next','esm_expiration_threshold':30,'esm_trigger':'esm trigger example'}}]
     * Checkbox: [{'esm':{'esm_type':3,'esm_title':'ESM Checkbox','esm_instructions':'The user can choose multiple options','esm_checkboxes':['One','Two','Other'],'esm_submit':'Next','esm_expiration_threshold':40,'esm_trigger':'esm trigger example'}}]
     * Likert: [{'esm':{'esm_type':4,'esm_title':'ESM Likert','esm_instructions':'User rating 1 to 5 or 7 at 1 step increments','esm_likert_max':5,'esm_likert_max_label':'Great','esm_likert_min_label':'Bad','esm_likert_step':1,'esm_submit':'OK','esm_expiration_threshold':50,'esm_trigger':'esm trigger example'}}]
     * Quick answer: [{'esm':{'esm_type':5,'esm_title':'ESM Quick Answer','esm_instructions':'One touch answer','esm_quick_answers':['Yes','No'],'esm_expiration_threshold':60,'esm_trigger':'esm trigger example'}}]
     * Scale: [{'esm':{'esm_type':6,'esm_title':'ESM Scale','esm_instructions':'User scaled value between minimum and maximum at X increments','esm_scale_min':0,'esm_scale_max':5,'esm_scale_start':3,'esm_scale_max_label':'5','esm_scale_min_label':'0','esm_scale_step':1,'esm_submit':'OK','esm_expiration_threshold':50,'esm_trigger':'esm trigger example'}}]
     * </p>
     * Furthermore, you can chain several mixed ESM together as a JSON array: [{esm:{}},{esm:{}},...]
     */
    public static final String EXTRA_ESM = "esm";

    /**
     * Extra for ACTION_AWARE_ESM_ANSWERED as String
     */
    public static final String EXTRA_ANSWER = "answer";

    public static final int ESM_NOTIFICATION_ID = 777;

    public static ESMNotificationTimeout esm_notif_expire = null;

    //Static instance to the notification manager
    private static NotificationManager mNotificationManager;

    @Override
    public void onCreate() {
        super.onCreate();

        DATABASE_TABLES = ESM_Provider.DATABASE_TABLES;
        TABLES_FIELDS = ESM_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{ESM_Data.CONTENT_URI};

        IntentFilter filter = new IntentFilter();
        filter.addAction(ESM.ACTION_AWARE_TRY_ESM);
        filter.addAction(ESM.ACTION_AWARE_QUEUE_ESM);
        filter.addAction(ESM.ACTION_AWARE_ESM_ANSWERED);
        filter.addAction(ESM.ACTION_AWARE_ESM_DISMISSED);
        filter.addAction(ESM.ACTION_AWARE_ESM_EXPIRED);
        registerReceiver(esmMonitor, filter);

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Aware.DEBUG) Log.d(TAG, "ESM service created!");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(esmMonitor);

        if (Aware.DEBUG) Log.d(TAG, "ESM service terminated...");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        boolean permissions_ok = true;
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                permissions_ok = false;
                break;
            }
        }

        if (permissions_ok) {
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

            Aware.setSetting(this, Aware_Preferences.STATUS_ESM, true);

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_ESM).equals("true")) {
                if (isESMWaiting(getApplicationContext()) && !isESMVisible(getApplicationContext())) {
                    notifyESM(getApplicationContext(), true);
                }
            }

            if (DEBUG)
                Log.d(TAG, "ESM service active... Queue = " + ESM_Queue.getQueueSize(getApplicationContext()));

        } else {
            Intent permissions = new Intent(this, PermissionsHandler.class);
            permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, REQUIRED_PERMISSIONS);
            permissions.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(permissions);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Check if we have NEW ESMs that can be answered at any time
     *
     * @param c
     * @return
     */
    public static boolean isESMWaiting(Context c) {
        boolean is_waiting = false;
        Cursor esms_waiting = c.getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data.STATUS + "=" + ESM.STATUS_NEW + " AND " + ESM_Data.EXPIRATION_THRESHOLD + "=0", null, ESM_Data.TIMESTAMP + " ASC LIMIT 1");
        if (esms_waiting != null && esms_waiting.moveToFirst()) {
            is_waiting = (esms_waiting.getCount() > 0);
        }
        if (esms_waiting != null && !esms_waiting.isClosed()) esms_waiting.close();
        return is_waiting;
    }

    /**
     * Check if we there is a VISIBLE ESMs that we are answering right now
     *
     * @param c
     * @return
     */
    public static boolean isESMVisible(Context c) {
        boolean is_visible = false;
        Cursor esms_waiting = c.getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data.STATUS + "=" + ESM.STATUS_VISIBLE, null, ESM_Data.TIMESTAMP + " ASC LIMIT 1");
        if (esms_waiting != null && esms_waiting.moveToFirst()) {
            is_visible = (esms_waiting.getCount() > 0);
        }
        if (esms_waiting != null && !esms_waiting.isClosed()) esms_waiting.close();
        return is_visible;
    }

    /**
     * Queue an ESM
     * @param context
     * @param queue
     */
    public static void queueESM(Context context, String queue) {
        queueESM(context, queue, false);
    }

    /**
     * Queue an ESM queue, but allowing trials
     *
     * @param context
     * @param queue
     */
    public static void queueESM(Context context, String queue, boolean isTrial) {
        try {
            JSONArray esms = new JSONArray(queue);

            long esm_timestamp = System.currentTimeMillis();
            boolean is_persistent = false;

            for (int i = 0; i < esms.length(); i++) {
                JSONObject esm = esms.getJSONObject(i).getJSONObject(EXTRA_ESM);

                ContentValues rowData = new ContentValues();
                rowData.put(ESM_Data.TIMESTAMP, esm_timestamp + i); //fix issue with synching and support ordering
                rowData.put(ESM_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                rowData.put(ESM_Data.JSON, esm.toString());
                rowData.put(ESM_Data.EXPIRATION_THRESHOLD, esm.optInt(ESM_Data.EXPIRATION_THRESHOLD)); //optional, defaults to 0
                rowData.put(ESM_Data.NOTIFICATION_TIMEOUT, esm.optInt(ESM_Data.NOTIFICATION_TIMEOUT)); //optional, defaults to 0
                rowData.put(ESM_Data.STATUS, ESM.STATUS_NEW);
                rowData.put(ESM_Data.TRIGGER, isTrial ? "TRIAL" : esm.optString(ESM_Data.TRIGGER)); //we use this TRIAL trigger to remove trials from database at the end of the trial

                if (i == 0 && (rowData.getAsInteger(ESM_Data.EXPIRATION_THRESHOLD) == 0 || rowData.getAsInteger(ESM_Data.NOTIFICATION_TIMEOUT) > 0)) {
                    is_persistent = true;
                }

                try {
                    context.getContentResolver().insert(ESM_Data.CONTENT_URI, rowData);
                    if (Aware.DEBUG) Log.d(TAG, "ESM: " + rowData.toString());
                } catch (SQLiteException e) {
                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                }
            }

            if (is_persistent) { //show notification

                Cursor pendingESM = context.getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data.STATUS + "=" + ESM.STATUS_NEW, null, ESM_Data.TIMESTAMP + " ASC LIMIT 1");
                if (pendingESM != null && pendingESM.moveToFirst()) {
                    //Set the timer if there is a notification timeout
                    int notification_timeout = pendingESM.getInt(pendingESM.getColumnIndex(ESM_Data.NOTIFICATION_TIMEOUT));
                    if (notification_timeout > 0) {
                        try {
                            ESM_Question question = new ESM_Question().rebuild(new JSONObject(pendingESM.getString(pendingESM.getColumnIndex(ESM_Data.JSON))));
                            esm_notif_expire = new ESMNotificationTimeout(context, System.currentTimeMillis(), notification_timeout, question.getNotificationRetry(), pendingESM.getInt(pendingESM.getColumnIndex(ESM_Data._ID)));
                            esm_notif_expire.execute();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
                if (pendingESM != null && !pendingESM.isClosed()) pendingESM.close();

                //Show notification
                notifyESM(context, true);

            } else { //show ESM immediately
                Intent intent_ESM = new Intent(context, ESM_Queue.class);
                intent_ESM.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent_ESM);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Show notification with ESM waiting
     *
     * @param context
     */
    public static void notifyESM(Context context, boolean notifyOnce) {

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context);
        mBuilder.setSmallIcon(R.drawable.ic_stat_aware_esm);
        mBuilder.setContentTitle(context.getResources().getText(R.string.aware_esm_questions_title));
        mBuilder.setContentText(context.getResources().getText(R.string.aware_esm_questions));
        mBuilder.setNumber(ESM_Queue.getQueueSize(context)); //update the number of ESMs queued
        mBuilder.setOngoing(true); //So it does not get cleared if the user presses clear all notifications.
        mBuilder.setUsesChronometer(true);
        mBuilder.setOnlyAlertOnce(notifyOnce);
        mBuilder.setDefaults(NotificationCompat.DEFAULT_ALL);

        Intent intent_ESM = new Intent(context, ESM_Queue.class);
        intent_ESM.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pending_ESM = PendingIntent.getActivity(context, 0, intent_ESM, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(pending_ESM);

        if (mNotificationManager == null)
            mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.notify(ESM_NOTIFICATION_ID, mBuilder.build());
    }

    public static class ESMNotificationTimeout extends AsyncTask<Void, Void, Void> {
        private long display_timestamp = 0;
        private int expires_in_seconds = 0;

        private Context mContext;
        private int mRetries = 0;

        public ESMNotificationTimeout(Context context, long display_timestamp, int expires_in_seconds, int retries, int esm_id) {
            this.display_timestamp = display_timestamp;
            this.expires_in_seconds = expires_in_seconds;
            this.mContext = context;
            this.mRetries = retries;
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (mRetries == 0) {
                while ((System.currentTimeMillis() - display_timestamp) / 1000 <= expires_in_seconds) {
                    if (isCancelled()) {
                        return null;
                    }
                }
            } else {
                while (mRetries > 0) {
                    while ((System.currentTimeMillis() - display_timestamp) / 1000 <= expires_in_seconds) {
                        if (isCancelled()) {
                            return null;
                        }
                    }
                    mRetries--;
                    display_timestamp = System.currentTimeMillis(); //move forward time and try again
                    if (Aware.DEBUG) Log.d(Aware.TAG, "Retrying ESM: " + mRetries);
                    notifyESM(mContext, false);
                }
            }

            if (Aware.DEBUG)
                Log.d(Aware.TAG, "ESM queue has expired!");

            //Remove notification
            if (mNotificationManager == null)
                mNotificationManager = (NotificationManager) mContext.getSystemService(NOTIFICATION_SERVICE);

            mNotificationManager.cancel(ESM.ESM_NOTIFICATION_ID);

            //Expire the queue
            Intent expired = new Intent(ESM.ACTION_AWARE_ESM_EXPIRED);
            mContext.sendBroadcast(expired);

            return null;
        }
    }

    //Singleton instance of this service
    private static ESM esmSrv = ESM.getService();

    /**
     * Get singleton instance to service
     *
     * @return ESM obj
     */
    public static ESM getService() {
        if (esmSrv == null) esmSrv = new ESM();
        return esmSrv;
    }

    private final IBinder serviceBinder = new ServiceBinder();

    public class ServiceBinder extends Binder {
        ESM getService() {
            return ESM.getService();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    /**
     * BroadcastReceiver for ESM module
     * - ACTION_AWARE_QUEUE_ESM
     * - ACTION_AWARE_ESM_ANSWERED
     * - ACTION_AWARE_ESM_DISMISSED
     * - ACTION_AWARE_ESM_EXPIRED
     *
     * @author df
     */
    public static class ESMMonitor extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!Aware.getSetting(context, Aware_Preferences.STATUS_ESM).equals("true")) return;

            if (intent.getAction().equals(ESM.ACTION_AWARE_TRY_ESM)) {
                queueESM(context, intent.getStringExtra(ESM.EXTRA_ESM), true);
            }

            if (intent.getAction().equals(ESM.ACTION_AWARE_QUEUE_ESM)) {
                queueESM(context, intent.getStringExtra(ESM.EXTRA_ESM), false);
            }

            if (intent.getAction().equals(ESM.ACTION_AWARE_ESM_ANSWERED)) {
                //Check if there is a flow to follow
                processFlow(context, intent.getStringExtra(EXTRA_ANSWER));

                if (ESM_Queue.getQueueSize(context) > 0) {
                    Intent intent_ESM = new Intent(context, ESM_Queue.class);
                    intent_ESM.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    context.startActivity(intent_ESM);
                } else {
                    if (Aware.DEBUG) Log.d(TAG, "ESM Queue is done!");
                    Intent esm_done = new Intent(ESM.ACTION_AWARE_ESM_QUEUE_COMPLETE);
                    context.sendBroadcast(esm_done);
                }
            }

            if (intent.getAction().equals(ESM.ACTION_AWARE_ESM_DISMISSED)) {
                Cursor esm = context.getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data.STATUS + " IN (" + ESM.STATUS_NEW + "," + ESM.STATUS_VISIBLE + ")", null, null);
                if (esm != null && esm.moveToFirst()) {
                    do {
                        ContentValues rowData = new ContentValues();
                        rowData.put(ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
                        rowData.put(ESM_Data.STATUS, ESM.STATUS_DISMISSED);
                        context.getContentResolver().update(ESM_Data.CONTENT_URI, rowData, ESM_Data._ID + "=" + esm.getInt(esm.getColumnIndex(ESM_Data._ID)), null);
                    } while (esm.moveToNext());
                }
                if (esm != null && !esm.isClosed()) esm.close();

                if (Aware.DEBUG) Log.d(TAG, "Rest of ESM Queue is dismissed!");

                Intent esm_done = new Intent(ESM.ACTION_AWARE_ESM_QUEUE_COMPLETE);
                context.sendBroadcast(esm_done);
            }

            if (intent.getAction().equals(ESM.ACTION_AWARE_ESM_EXPIRED)) {
                Cursor esm = context.getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data.STATUS + " IN (" + ESM.STATUS_NEW + "," + ESM.STATUS_VISIBLE + ")", null, null);
                if (esm != null && esm.moveToFirst()) {
                    do {
                        ContentValues rowData = new ContentValues();
                        rowData.put(ESM_Provider.ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
                        rowData.put(ESM_Provider.ESM_Data.STATUS, ESM.STATUS_EXPIRED);
                        context.getContentResolver().update(ESM_Data.CONTENT_URI, rowData, ESM_Data._ID + "=" + esm.getInt(esm.getColumnIndex(ESM_Data._ID)), null);
                    } while (esm.moveToNext());
                }
                if (esm != null && !esm.isClosed()) esm.close();

                if (Aware.DEBUG) Log.d(TAG, "Rest of ESM Queue is expired!");
                Intent esm_done = new Intent(ESM.ACTION_AWARE_ESM_QUEUE_COMPLETE);
                context.sendBroadcast(esm_done);
            }
        }
    }

    private static void processFlow(Context context, String current_answer) {
        if (ESM.DEBUG) {
            Log.d(ESM.TAG, "Current answer: " + current_answer);
        }

        try {
            //Check flow
            Cursor last_esm = context.getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data.STATUS + "=" + ESM.STATUS_ANSWERED, null, ESM_Data.TIMESTAMP + " DESC LIMIT 1");
            if (last_esm != null && last_esm.moveToFirst()) {

                JSONObject esm_question = new JSONObject(last_esm.getString(last_esm.getColumnIndex(ESM_Data.JSON)));
                ESM_Question esm = new ESMFactory().getESM(esm_question.getInt(ESM_Question.esm_type), esm_question, last_esm.getInt(last_esm.getColumnIndex(ESM_Data._ID)));

                //Set as branched the flow rules that are not triggered
                JSONArray flows = esm.getFlows();
                for (int i = 0; i < flows.length(); i++) {
                    JSONObject flow = flows.getJSONObject(i);
                    String flowAnswer = flow.getString(ESM_Question.flow_user_answer);
                    JSONObject nextESM = flow.getJSONObject(ESM_Question.flow_next_esm).getJSONObject(EXTRA_ESM);

                    if (flowAnswer.equals(current_answer)) {
                        if (ESM.DEBUG) Log.d(ESM.TAG, "Following next question: " + nextESM);

                        //Queued ESM
                        ContentValues rowData = new ContentValues();
                        rowData.put(ESM_Data.TIMESTAMP, System.currentTimeMillis()); //fixed issue with synching and support ordering of esms by timestamp
                        rowData.put(ESM_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                        rowData.put(ESM_Data.JSON, nextESM.toString());
                        rowData.put(ESM_Data.EXPIRATION_THRESHOLD, nextESM.optInt(ESM_Data.EXPIRATION_THRESHOLD)); //optional, defaults to 0
                        rowData.put(ESM_Data.NOTIFICATION_TIMEOUT, nextESM.optInt(ESM_Data.NOTIFICATION_TIMEOUT)); //optional, defaults to 0
                        rowData.put(ESM_Data.STATUS, ESM.STATUS_NEW);
                        rowData.put(ESM_Data.TRIGGER, nextESM.optString(ESM_Data.TRIGGER)); //optional, defaults to ""

                        context.getContentResolver().insert(ESM_Data.CONTENT_URI, rowData);
                    } else {
                        if (ESM.DEBUG)
                            Log.d(ESM.TAG, "Branched split: " + flowAnswer + " Skipping: " + nextESM);

                        //Branched ESM
                        ContentValues rowData = new ContentValues();
                        rowData.put(ESM_Data.TIMESTAMP, System.currentTimeMillis()); //fixed issue with synching and support ordering of esms by timestamp
                        rowData.put(ESM_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                        rowData.put(ESM_Data.JSON, nextESM.toString());
                        rowData.put(ESM_Data.EXPIRATION_THRESHOLD, nextESM.optInt(ESM_Data.EXPIRATION_THRESHOLD)); //optional, defaults to 0
                        rowData.put(ESM_Data.NOTIFICATION_TIMEOUT, nextESM.optInt(ESM_Data.NOTIFICATION_TIMEOUT)); //optional, defaults to 0
                        rowData.put(ESM_Data.STATUS, ESM.STATUS_BRANCHED);
                        rowData.put(ESM_Data.TRIGGER, nextESM.optString(ESM_Data.TRIGGER)); //optional, defaults to ""

                        context.getContentResolver().insert(ESM_Data.CONTENT_URI, rowData);
                    }
                }
            }
            if (last_esm != null && !last_esm.isClosed()) last_esm.close();

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static final ESMMonitor esmMonitor = new ESMMonitor();
}