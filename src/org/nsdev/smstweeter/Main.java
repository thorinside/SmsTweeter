/**
 * Copyright 2010 Neal Sanche
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.nsdev.smstweeter;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

public class Main extends Activity {
    private static final int MENU_QUIT = 0;
    public static final String PREFS_NAME = "SMSTweeterPrefs";

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        Button b = (Button)findViewById(R.id.ButtonSavePreferences);
        final EditText twitterUser = (EditText)findViewById(R.id.TwitterUser);
        final EditText twitterPassword = (EditText)findViewById(R.id.TwitterPassword);
        final CheckBox forwardingEnabled = (CheckBox)findViewById(R.id.CheckBoxForwardingEnabled);

		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		
		twitterUser.setText(settings.getString("twitterUser", ""));
		twitterPassword.setText(settings.getString("twitterPassword", ""));
		forwardingEnabled.setChecked(settings.getBoolean("forwardingEnabled", false));
		
		final Context context = this.getApplicationContext();

        b.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
				SharedPreferences.Editor editor = settings.edit();
				editor.putString("twitterUser", twitterUser.getText().toString());
				editor.putString("twitterPassword", twitterPassword.getText().toString());
				editor.putBoolean("forwardingEnabled", forwardingEnabled.isChecked());
				
				if (editor.commit())
				{
					Toast.makeText(context, "Saved", Toast.LENGTH_SHORT);
				}
			}});
    }
    
    public boolean onCreateOptionsMenu(Menu menu)
    {
    	menu.add(0, MENU_QUIT, 0, "Quit");
    	return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	switch (item.getItemId()) {
    	case MENU_QUIT:
    		finish();
    		return true;
    	}
    	
    	return false;
    }
    
}