/**
 * 
 */
package org.nsdev.smstweeter;

import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.gsm.SmsManager;
import android.telephony.gsm.SmsMessage;
import android.util.Log;
import android.widget.Toast;

/**
 * @author Neal
 */
public class SmsReceiver extends BroadcastReceiver {

	static AsyncTask<String, String, String> mPoller = null;
	static Object mWaitLock = new Object();
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
	 * android.content.Intent)
	 */
	@Override
	public void onReceive(final Context context, Intent intent) {
		// ---get the SMS message passed in---
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		
		final SmsMessage[] msgs = getMessagesFromIntent(intent);

		StringBuilder str = new StringBuilder();
		for (int i = 0; i < msgs.length; i++) {
			str.append(msgs[0].getOriginatingAddress());
			str.append(": ");
			str.append(msgs[i].getMessageBody().toString());
			str.append("\n");
		}
		// ---display the new SMS message---
		Toast.makeText(context, str, Toast.LENGTH_SHORT).show();
		
		final String msg = str.toString();

		SharedPreferences settings = context.getSharedPreferences(Main.PREFS_NAME, 0);
		
		final String twitterUser = settings.getString("twitterUser", "");
		final String twitterPassword = settings.getString("twitterPassword", "");
		final boolean forwardingEnabled = settings.getBoolean("forwardingEnabled", false);
		
		// Kick out if it's turned off
		if (twitterUser.length() == 0) return;
		if (twitterPassword.length() == 0) return;
		if (!forwardingEnabled) return;
		
		final int responsePollingLength = settings.getInt("responsePollingMinutes", 60);

		try {
			final Twitter twitter = new Twitter(twitterUser,twitterPassword);
			twitter.setSource("sms");
			
			// Update status with annotations specifying the receiver phone number
			String annotation = "[{\"sms\":{\"phone\":\"" + msgs[0].getOriginatingAddress() +"\"}}]" ;
			final Twitter.Status myMessage = twitter.updateStatus(msg,annotation,-1);

			Log.i("SMSRECIEVER","Update: "+msg);
			
			if (mPoller == null) {
				mPoller = new AsyncTask<String, String, String>() {
					@Override
					protected String doInBackground(String... params) {
						Log.i("SMSWaiter", "New poller created.");
						try {
							Date lastReplyDate = myMessage.getCreatedAt();
							long cutoffTime = System.currentTimeMillis() + (responsePollingLength * 60000);
							long pollingStarted = System.currentTimeMillis();

							while (System.currentTimeMillis() < cutoffTime) {
								
								synchronized(mWaitLock) {
									long pollInterval = CalculatePollInterval(pollingStarted,System.currentTimeMillis(),cutoffTime);
									long waitStarted = System.currentTimeMillis();

									Log.d("SMSWaiter","Waiting for "+pollInterval+" ms");
									
									mWaitLock.wait(pollInterval);
									
									if (System.currentTimeMillis() - waitStarted < pollInterval)
									{
										Log.i("SMSWaiter","Wait interrupted, resetting cutoffTime.");

										// Resetting cutoff date
										pollingStarted = System.currentTimeMillis();
										cutoffTime = pollingStarted + (responsePollingLength * 60000);
									}
								}

								Log.i("SMSWaiter", "Polling...");
								try {
									for (Twitter.Status msg : twitter.getReplies()) {
										if (msg.getCreatedAt().after(lastReplyDate))
										{
											Log.i("SMSWaiter", "New reply found...");
	
											// Found something newer
											lastReplyDate = msg.getCreatedAt();
	
											// Resetting cutoff date
											pollingStarted = System.currentTimeMillis();
											cutoffTime = pollingStarted + (responsePollingLength * 60000);
											
											// Ask twitter for the original message
											Twitter.Status originalStatus = twitter.getStatus(msg.inReplyToStatusId);
											
											Log.i("SMSWaiter", "Retrieved original status: "+originalStatus.text);
											
											String messageText = msg.getText();
	
											if (messageText.startsWith("@"+ twitterUser))
												messageText = messageText
														.substring(("@" + twitterUser).length());
											
											messageText = messageText.trim();
	
											Log.i("SMSWaiter",
													"Found reply message: "
															+ messageText);
	
											String phoneNumber = null;
	
											try {
												String annotations = originalStatus.getAnnotations();
												annotations = annotations.substring(1, annotations.length() - 1);
												JSONObject annotationObject = new JSONObject(annotations);
												JSONObject sms = annotationObject.getJSONObject("sms");
												phoneNumber = (String) sms.get("phone");
												Log.i("SMSWaiter", "phone="
														+ phoneNumber);
											} catch (JSONException e) {
												// TODO Auto-generated catch block
												Log.e("SMSWaiter", e.toString());
											}
	
											if (phoneNumber != null) {
												try {
													SmsManager sms = SmsManager.getDefault();
													sms.sendTextMessage(
																	phoneNumber,
																	null,
																	messageText,
																	null, null);
													Log.i("SMSWaiter",
															"Sent SMS message to "
																	+ phoneNumber
																	+ ": "
																	+ messageText);
												} catch (Throwable ex) {
													Log.e("SMSWaiter", ex
															.toString());
												}
											}
										}
									}
								} catch (TwitterException ex)
								{
									if (ex.toString().contains("RateLimit"))
									{
										while (twitter.getRateLimitStatus() < 0)
										{
											Log.e("SMSWaiter","Rate limited. Sleeping for a bit.");
											Thread.sleep(15000);
										}
									}
								}
							}
							Log.i("SMSWaiter", "Timed out, quitting.");
						} catch (InterruptedException ex) {
							Log.e("SMSWaiter", "Interrupted.");
						} catch (Throwable ex) {
							Log.e("SMSWaiter", "Died: "+ex.toString());
						}
						mPoller = null;
						return null;
					}

				};
				mPoller.execute("DoIt!");
			} else {
				Log.i("SMSRECIEVER","Poller already exists.");
				synchronized(mWaitLock)
				{
					mWaitLock.notifyAll();
				}
			}
		} catch (Throwable ex) {
			Log.e("SMSRECIEVER",ex.toString());
		}
	}
	
	long intervalStep = 30000L;
	long[] pollIntervalLookupTable = { intervalStep, intervalStep*2, intervalStep*4, intervalStep*10, intervalStep*20 };

	private long CalculatePollInterval(long pollingStarted,
			long currentTimeMillis, long cutoffTime) {
		
		double percent = (currentTimeMillis - pollingStarted) / (double)(cutoffTime - pollingStarted);
		
		int index = (int)((percent * 100) / (double)20);
		
		Log.d("SMSWaiter","percent = "+percent+" index="+index);
		
		long interval = pollIntervalLookupTable[index];
		if (currentTimeMillis + interval > cutoffTime) {
			interval = cutoffTime - currentTimeMillis;
		}
		return interval;
	}

	private SmsMessage[] getMessagesFromIntent(Intent intent) {
		SmsMessage retMsgs[] = null;
		Bundle bdl = intent.getExtras();
		try {
			Object pdus[] = (Object[]) bdl.get("pdus");
			retMsgs = new SmsMessage[pdus.length];
			for (int n = 0; n < pdus.length; n++) {
				byte[] byteData = (byte[]) pdus[n];
				retMsgs[n] = SmsMessage.createFromPdu(byteData);
			}
		} catch (Exception e) {
			Log.e("GetMessages", "fail", e);
		}
		return retMsgs;
	}

}
