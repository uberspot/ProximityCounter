/**
MIT/X Consortium License 

� uberspot <Paul Sarbinowski>

Permission is hereby granted, free of charge, to any person obtaining a
copy of this software and associated documentation files (the "Software"),
to deal in the Software without restriction, including without limitation
the rights to use, copy, modify, merge, publish, distribute, sublicense,
and/or sell copies of the Software, and to permit persons to whom the
Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
DEALINGS IN THE SOFTWARE.

 */
package com.uberspot.proximitycounter;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

public class MainPage extends Activity implements SensorEventListener {

	private final static String preferencesName = "PCounterPrefs";
	private SensorManager sensorManager;
	private Sensor proximitySensor;
	private TextView outTextView;
	private Button startButton;
	private CheckBox checkBox;
	private EditText maxCounterText;
	private int counter = 0, counterMax;
	private boolean started;
	private int repsCompleted = 0;
	private long previousTimestamp = 0 ;
	private static final long timeThreshold=400000000; //0.4 seconds expressed in nanoseconds
	private static final int defaultMaxCount= 20; 
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_page);
        
        outTextView = (TextView)findViewById(R.id.outTextView);
        startButton = (Button)findViewById(R.id.startButton);
        maxCounterText = (EditText)findViewById(R.id.countLimit);
        checkBox = (CheckBox) findViewById(R.id.restart_checkbox);
        checkBox.setChecked(getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
				.getBoolean("autoRestart", true));
        maxCounterText.addTextChangedListener(new TextWatcher(){ 
        	public void afterTextChanged(Editable s) {
        		counterMax = getMaxCounterText();
        		if(counter>= counterMax){
    				onClearClick(null);
    			}
	        }
	        public void beforeTextChanged(CharSequence s, int start, int count, int after){}
	        public void onTextChanged(CharSequence s, int start, int before, int count){}
	        });
        started = false; 
        maxCounterText.setText(getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        				.getInt("maxCount", defaultMaxCount) + "");
        
        // Get an instance of the sensor service, and use that to get an instance of
        // a particular sensor.
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (proximitySensor == null){
        	outTextView.setText(getString(R.string.no_sensor_warning));
        	finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_page, menu);
        return false;
    }


	@Override
	public void onAccuracyChanged(Sensor sensor, int change) { }

	/** Called when the sensor detects a change in it's reading
	 */
	@Override
	public void onSensorChanged(SensorEvent event) {
		if(event.sensor.getType()==Sensor.TYPE_PROXIMITY && started && 
						(event.timestamp-previousTimestamp > timeThreshold) 
						&& (event.values[0] < 1) ){
			outTextView.setText(getString(R.string.count) + ":" + ++counter + "\n" 
						+ getString(R.string.reps_completed) + ": " + repsCompleted);
			previousTimestamp = event.timestamp;
			if(counter>= counterMax){
				if(checkBox.isChecked()) {
					repsCompleted++;
					clearCount(); //restart automatically
				} else {
					onStartClick(null);
				}
				playNotification();
			}
		}
	}

	/** Plays a sound notification to alert the user.
	 */
	public void playNotification() {
		try {
		    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
		    Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
		    r.play();
		} catch (Exception e) {}
	}
	
	@Override
	protected void onResume() {
	    // Register a listener for the sensor.
	    super.onResume();
	    if (sensorManager!=null && proximitySensor != null) {
	    	sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
	    }
	}

	@Override
	protected void onPause() {
	    // Be sure to unregister the sensor when the activity pauses.
	    super.onPause();
	    if (sensorManager!=null) {
	    	sensorManager.unregisterListener(this);
	    }
	}
	
	@Override
	public void onDestroy() {
		//Save changes in preferences
    	SharedPreferences.Editor editor = getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit();
  	    editor.putInt("maxCount", getMaxCounterText());
  	    editor.putBoolean("autoRestart", checkBox.isChecked());
  	    editor.commit();
  	    
		super.onDestroy();
		this.finish();
	}
	
	/** Called when the Start button is clicked.
	 * @param v
	 */
    public void onStartClick(View v) {
    	started = !started ;
    	if(started)
    		startButton.setText(getString(R.string.stop));
    	else
    		startButton.setText(getString(R.string.start));
    	clearCount();
    }

    /** Called when the Clear button is clicked. Defaults the current count to zero.
     * @param v
     */
    public void onClearClick(View v) {
    	repsCompleted = 0;
    	clearCount();
    }

	public void clearCount() {
		counter = 0;
    	outTextView.setText(getString(R.string.count) + ":" + counter+ "\n" 
				+ getString(R.string.reps_completed) + ": " + repsCompleted);
	}
    
    /** Returns the number in the EditText with id countLimit
     * @return
     */
    private int getMaxCounterText(){
    	try {
    		return Integer.valueOf(maxCounterText.getText().toString());
    	}catch(NumberFormatException e){
    		return defaultMaxCount;
    	}
    }
}
