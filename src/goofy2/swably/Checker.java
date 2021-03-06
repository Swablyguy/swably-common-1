package goofy2.swably;

import goofy2.swably.R;
import goofy2.swably.fragment.FeedsFragment;
import goofy2.utils.JSONUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;

public class Checker extends CloudAlarmService {
	public static final int NOTIFICATION_ID = 3827;
	public static int MIN_INTERVAL = 20*1000;
	public static int MAX_INTERVAL = 5*60*1000;
	public static final String MESSAGE_RECEIVED = "goofy2.swably.MESSAGE_RECEIVED";

	public Checker() {
		super();
	}

	public Checker(Context context) {
		super(context);
	}

	@Override
	public void onCreate(){
		super.onCreate();
        //SimpleDateFormat datetimeFormat = new SimpleDateFormat(Const.UPLOAD_DATE_TIME_FORMAT);
		//mSinceTime = datetimeFormat.format(new Date()).replaceAll(" ", "%20");
		//mFeedHelper.open();
		Log.d(Const.APP_NAME, Const.APP_NAME + " Checker - create service");
	}

	@Override
	public void onDestroy(){
		Log.d(Const.APP_NAME, Const.APP_NAME + " Checker - destroy service");
		//mFeedHelper.close();
    	super.onDestroy();
	}
	@Override
	protected void onHandleIntent(Intent intent) {
		if(isNoticeOn(this)){ // the Alarm may not be canceled in some phones such as milestone
			try{
				run();
			}catch(Exception e){
				e.printStackTrace();
			}finally{
				setNextRun(Checker.class);
			}
		}
	}

//	@Override
//	public void onStart(Intent intent, int startId){
//		super.onStart(intent, startId);
//	}
	
	public void run(){
		try{
			Log.d(Const.APP_NAME, Const.APP_NAME + " Checker: run");
			increaseInterval();
			
			if(updated()) fetch();
		
//			if((System.currentTimeMillis()/1000 - getLastCheckSnsJoinTime(this)) > 8*3600)
//				checkSnsJoin();

		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
    static public void cancelNextRun(Context context){
    	CloudAlarmService.cancelNextRun(context, Checker.class);
    }
    
	static public void checkSnsJoin(Context context){
		String cacheId = SnsFriendsFragment.cacheId();
		String strCache = Utils.loadCache(context, cacheId);

		if(strCache == null || (System.currentTimeMillis() - Utils.getCacheAt(context,cacheId)) > SnsFriendsFragment.cacheExpiresIn()){
			String snsId = Utils.getCurrentUser(context).optString("signup_sns");
		    String url = Const.HTTP_PREFIX + "/connections/find_friends/"+snsId+"?format=json&check=true&"+Utils.getLoginParameters(context)+"&"+Utils.getClientParameters(context);
			String strResult = null;
			String err = null;
			
			try{
				Log.d(Const.APP_NAME, Const.APP_NAME + " checkSnsJoin run");
				HttpPost httpReq = new HttpPost(url);
				List <NameValuePair> params = new ArrayList <NameValuePair>();
				params.add(new BasicNameValuePair("contacts", SnsFriendsFragment.getContactsEmails(context)));
				httpReq.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
				HttpParams httpParameters = new BasicHttpParams();
				HttpConnectionParams.setConnectionTimeout(httpParameters, Const.HTTP_TIMEOUT);
				httpReq.setParams(httpParameters);
				HttpResponse httpResp = new DefaultHttpClient().execute(httpReq);
				strResult = EntityUtils.toString(httpResp.getEntity());
				int code = httpResp.getStatusLine().getStatusCode(); 
				if( code != 200){
					JSONObject json = new JSONObject(strResult);
					err = json.getString("error_message");
				}
			}catch (Exception e){
				err = e.getMessage();
				Log.e(Const.APP_NAME, Const.APP_NAME + " checkSnsJoin err: " + err);
			}finally{
				setLastCheckSnsJoinTime(context, System.currentTimeMillis()/1000);
			}
			
			if(err == null){
				try {
					JSONArray latest = new JSONArray(strResult);
					if(strCache != null){
						JSONArray cached = new JSONArray(strCache);
						int lastTime = cached.getJSONObject(0).optInt("created_at");
						JSONArray newJoins = new JSONArray();
						for(int i=0; i<latest.length(); i++){
							if(latest.getJSONObject(i).optInt("created_at") > lastTime){
								newJoins.put(latest.getJSONObject(i));
							}
						}
						if(newJoins.length() > 0) notifyJoins(context, newJoins);
					}
					Utils.cacheData(context, latest.toString(), cacheId);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	 
    static private void notifyJoins(Context context, JSONArray newJoins) {
		if(!isNoticeOn(context)) return;
    	int thisCount = newJoins.length();
    	if(thisCount == 0) return;

    	String text;
		String expandedText;
		String expandedTitle;
		Intent i;
    	if(thisCount == 1){
    		JSONObject user = newJoins.optJSONObject(0); 
        	text = String.format(context.getString(R.string.joining_noti_title), user.optString("name"));
    		expandedTitle = text;
    		expandedText = "";
    	}else{
        	text = String.format(context.getString(R.string.new_joins_count), thisCount);
        	expandedTitle = text;
        	expandedText = context.getString(R.string.tap_to_check);
    	}
//		i = new Intent(context, People.class);
//		i.setData(Uri.parse("0")); // initial at sns friends tab
		i = new Intent(context, SnsFriends.class);

    	NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
		Notification notification = Utils.getDefaultNotification(text);
		PendingIntent launchIntent = PendingIntent.getActivity(context, newJoins.hashCode(), i, PendingIntent.FLAG_ONE_SHOT);
		notification.setLatestEventInfo(context, expandedTitle, expandedText, launchIntent);
		nm.notify(NOTIFICATION_ID+2, notification);
	}

	public void fetch(){
    	//if(!Utils.isNetworkAvailable(this)) return;
		if(!isNoticeOn(this)) return;
    	JSONObject currentUser = Utils.getCurrentUser(this);
    	if(currentUser == null) return;
		String err = null;
		String strResult = null;
		JSONObject json = null;
		try{
			String params = "?format=json&user_id="+currentUser.getString("id")+"&user_key="+currentUser.getString("key")+"&since="+FeedsFragment.getLastReadTime(this)+"&"+Utils.getClientParameters(this);
			HttpGet httpReq = new HttpGet(Const.HTTP_PREFIX+"/feeds/fetch2"+params);
			HttpParams httpParameters = new BasicHttpParams();
			HttpConnectionParams.setConnectionTimeout(httpParameters, Const.HTTP_TIMEOUT);
			httpReq.setParams(httpParameters);
			HttpResponse httpResp = new DefaultHttpClient().execute(httpReq);
			strResult = EntityUtils.toString(httpResp.getEntity());
			json = new JSONObject(strResult);
			if(httpResp.getStatusLine().getStatusCode() == 200){
				int count = json.getInt("count");
				double fetch_time = json.getDouble("fetch_time");
				setLastFetchTime(this, fetch_time);
				if(count > 0){
					Log.d(Const.APP_NAME, Const.APP_NAME + " Checker - fetch: " + count);
//					sumUnreadCount(json.getInt("reviews_count"), json.getInt("follows_count"));
					sendBroadcast(new Intent(MESSAGE_RECEIVED));
//					notifyReviews(json);
//					notifyFollows(json);
					notify(json);
					resetInterval(this);
				}
			}else{
				err = json.getString("error_message");
			}
		}catch (Exception e){
			err = e.getMessage();
	    	Log.e(Const.APP_NAME, Const.APP_NAME + " Checker - fetch: " + err);
		}
		
    	if(err != null) Log.d(Const.APP_NAME, Const.APP_NAME + " Checker - fetch: " + err);
    }
    
//	private void sumUnreadCount(int reviewCount, int followCount) {
//		Utils.setUnreadReviewsCount(this, Utils.getUnreadReviewsCount(this)+reviewCount);
//		Utils.setUnreadFollowsCount(this, Utils.getUnreadFollowsCount(this)+followCount);
//	}

	private boolean updated(){
		Log.d(Const.APP_NAME, Const.APP_NAME + " Checker: check if updated");
    	//if(!Utils.isNetworkAvailable(this)) return false;
    	JSONObject currentUser = Utils.getCurrentUser(this);
    	if(currentUser == null) return false;
    	boolean ret = false;
		String err = null;
		String strResult = null;
		try{
			HttpGet httpReq = new HttpGet(Const.HTTP_PREFIX+"/feeds/check/"+currentUser.getString("id"));
			HttpParams httpParameters = new BasicHttpParams();
			HttpConnectionParams.setConnectionTimeout(httpParameters, Const.HTTP_TIMEOUT_SHORT);
			httpReq.setParams(httpParameters);
			HttpResponse httpResp = new DefaultHttpClient().execute(httpReq);
			strResult = EntityUtils.toString(httpResp.getEntity());
			if(httpResp.getStatusLine().getStatusCode() == 200){
				double cacheTime  = Double.parseDouble(strResult);
				if(cacheTime > getLastFetchTime(this)) ret = true;
			}else{
				err = strResult;
			}
		}catch (Exception e){
			err = e.getMessage();
		}
			
    	if(err != null) Log.d(Const.APP_NAME, Const.APP_NAME + " Checker - updated() err: " + err);
    	return ret;
    }


//    private void notifyReviews(JSONObject json) throws JSONException{
//		if(!isNoticeOn(this)) return;
//    	int unreadCount = Utils.getUnreadReviewsCount(this);
//    	int thisCount = json.getInt("reviews_count");
//    	if(thisCount == 0) return;
//    	
//		String text;
//		String expandedText;
//		String expandedTitle;
//		Intent i;
//    	if(thisCount == 1){
//    		JSONObject review = json.getJSONObject("recent_review");
//    		JSONObject jsonApp = review.optJSONObject("app");
//    		if(jsonApp == null)	text = review.getJSONObject("user").getString("name") + ": " + review.getString("content");
//    		else text = review.getJSONObject("user").getString("name") + ": #" +  jsonApp.getString("name") + " " + review.getString("content");	
//    		expandedTitle = review.getJSONObject("user").getString("name");
//    		if(jsonApp == null) expandedText = review.getString("content");
//    		else expandedText = "#" +  review.getJSONObject("app").getString("name") + " " + review.getString("content");
//    		i = new Intent(this, ReviewProfile.class);
//    		i.putExtra(Const.KEY_REVIEW, review.toString());
////    		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//    	}else{
//    		text = String.format(getString(R.string.new_reviews_count), thisCount);
//    		expandedTitle = String.format(getString(R.string.new_reviews_count), thisCount);
//    		expandedText = json.getString("review_names");
////    		i = new Intent(this, Home.class);
////    		i.setData(Uri.parse("following")); // initial at following tab
////    		i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//    		i = new Intent(this, MyFollowingReviews.class);
//    		
//    	}
//    	
//		NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
//		Notification notification = Utils.getDefaultNotification(text);
//		//if(BannkaSettings.getSound(this)) notification.sound = Uri.parse(BannkaSettings.getRingtone(this));
//		//if(BannkaSettings.getVibration(this)) notification.defaults = Notification.DEFAULT_VIBRATE;
////		Intent i = new Intent(this, MyFollowingReviews.class);
//		PendingIntent launchIntent = PendingIntent.getActivity(this, json.hashCode(), i, PendingIntent.FLAG_ONE_SHOT);
//		notification.setLatestEventInfo(this, expandedTitle, expandedText, launchIntent);
//		nm.notify(NOTIFICATION_ID, notification);
//    }
//
//    private void notifyFollows(JSONObject json) throws JSONException{
//		if(!isNoticeOn(this)) return;
//    	int unreadCount = Utils.getUnreadFollowsCount(this);
//    	int thisCount = json.getInt("follows_count");
//    	if(thisCount == 0) return;
//
//    	String text;
//		String expandedText;
//		String expandedTitle;
//		Intent i;
//    	if(thisCount == 1){
//    		JSONObject user = json.getJSONObject("recent_follower"); 
//        	text = String.format(getString(R.string.following_noti_title), user.optString("name"));
//    		expandedTitle = text;
//    		expandedText = "";
//    		i = new Intent(this, User.class);
//    		i.putExtra(Const.KEY_USER, user.toString());
//    	}else{
//        	text = String.format(getString(R.string.new_follows_count), thisCount);
//        	expandedTitle = text;
//        	expandedText = json.getString("follow_names");
////    		i = new Intent(this, Me.class);
////    		i.setData(Uri.parse("followers")); // initial at followers tab
//    		i = new Intent(this, UserFollowers.class);
//        	i.putExtra(Const.KEY_USER, Utils.getCurrentUser(this).toString());
//    	}
////		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//
//    	NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
//		Notification notification = Utils.getDefaultNotification(text);
//		//if(BannkaSettings.getSound(this)) notification.sound = Uri.parse(BannkaSettings.getRingtone(this));
//		//if(BannkaSettings.getVibration(this)) notification.defaults = Notification.DEFAULT_VIBRATE;
////		Intent i = new Intent(this, UserFollowers.class);
////		i.putExtra(Const.KEY_USER, Utils.getCurrentUser(this).toString());
//		PendingIntent launchIntent = PendingIntent.getActivity(this, json.hashCode(), i, PendingIntent.FLAG_ONE_SHOT);
//		notification.setLatestEventInfo(this, expandedTitle, expandedText, launchIntent);
//		nm.notify(NOTIFICATION_ID+1, notification);
//    }

    private void notify(JSONObject json) throws JSONException{
		if(!isNoticeOn(this)) return;
    	int unreadCount = json.getInt("count");
    	if(unreadCount == 0) return;
    	
		String text;
		String expandedText;
		String expandedTitle;
		Intent i;
    	if(unreadCount == 1){
    		JSONObject feed = json.getJSONObject("recent_feed");
    		text = feed.getJSONObject("producer").getString("name") + " " + feed.getString("title");
    		expandedTitle = text;
    		expandedText = feed.getString("content");
//    		i = new Intent(this, Feeds.class);
//    		i.putExtra(Const.KEY_REVIEW, review.toString());
//    		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    		i = FeedsFragment.getFeedIntent(this, feed);
    	}else{
    		text = String.format(getString(R.string.new_feeds_count), unreadCount);
    		expandedTitle = String.format(getString(R.string.new_feeds_count), unreadCount);
    		expandedText = json.getString("names");
    		i = new Intent(this, Feeds.class);
    	}
    	
		NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		Notification notification = Utils.getDefaultNotification(text);
		//if(BannkaSettings.getSound(this)) notification.sound = Uri.parse(BannkaSettings.getRingtone(this));
		//if(BannkaSettings.getVibration(this)) notification.defaults = Notification.DEFAULT_VIBRATE;
//		Intent i = new Intent(this, MyFollowingReviews.class);
		PendingIntent launchIntent = PendingIntent.getActivity(this, json.hashCode(), i, PendingIntent.FLAG_ONE_SHOT);
		notification.setLatestEventInfo(this, expandedTitle, expandedText, launchIntent);
		nm.notify(NOTIFICATION_ID, notification);
    }
    
    public double getLastFetchTime(Context context){
		String strTime = Utils.getUserPrefString(context, Utils.getCurrentUserId(context) + "lastFetchTime", null);
		double time;
		if(strTime == null){
			//time = System.currentTimeMillis()/1000 - 30*3600*24;
			time = System.currentTimeMillis()/1000;
			setLastFetchTime(context, time);
		}
		else time = Double.parseDouble(strTime);
		return time;
	}
	public void setLastFetchTime(Context context, double value){
		Utils.setUserPrefString(context, Utils.getCurrentUserId(context) + "lastFetchTime", Double.toString(value));
	}
	
    public double getLastCheckSnsJoinTime(Context context){
		String strTime = Utils.getUserPrefString(context, Utils.getCurrentUserId(context) + "lastCheckSnsJoinTime", null);
		double time;
		if(strTime == null){
			//time = System.currentTimeMillis()/1000 - 30*3600*24;
			time = System.currentTimeMillis()/1000;
			setLastCheckSnsJoinTime(context, time);
		}
		else time = Double.parseDouble(strTime);
		return time;
	}
	static public void setLastCheckSnsJoinTime(Context context, double value){
		Utils.setUserPrefString(context, Utils.getCurrentUserId(context) + "lastCheckSnsJoinTime", Double.toString(value));
	}

	static public boolean isNoticeOn(Context context){
//		String pref = Utils.getUserPrefString(context, "notification", "on");
//		return pref.equalsIgnoreCase("on");
		return Settings.getNotification(context);
//		return false; // disable notice;
	}

	protected int getMinInterval(){
		return this.MIN_INTERVAL;
	}
	protected int getMaxInterval(){
		return this.MAX_INTERVAL;
	}
}
