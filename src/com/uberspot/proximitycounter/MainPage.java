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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

public class MainPage extends Activity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor proximitySensor;
    private TextView outTextView;
    private Button startButton, clearButton;
    private CheckBox autoRestartCheckBox;
    private EditText maxCounter;
    private int currentCounter = 0, counterMax, repsCompleted = 0;
    private boolean started;
    private long previousTimestamp = 0;
    private static final long timeThreshold = 400000000; //0.4 seconds expressed in nanoseconds
    private static final int defaultMaxCount = 20;

    private static final String AUTO_RESTART_PREF = "autoRestart";
    private static final String MAX_COUNT_PREF = "maxCount";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_page);

        outTextView = (TextView) findViewById(R.id.outTextView);
        startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                toggleCounting();
            }
        });
        clearButton = (Button) findViewById(R.id.clearButton);
        clearButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                clearRepsAndCount();
            }
        });
        maxCounter = (EditText) findViewById(R.id.countLimit);

        autoRestartCheckBox = (CheckBox) findViewById(R.id.restart_checkbox);
        autoRestartCheckBox.setChecked(
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getBoolean(AUTO_RESTART_PREF, true));
        maxCounter.addTextChangedListener(new TextWatcher(){
               @Override
            public void afterTextChanged(Editable s) {
                       counterMax = getMaxCounterText();
                       if(currentCounter>= counterMax){
                           clearRepsAndCount();
                       }
               }
               @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after){}
               @Override
                public void onTextChanged(CharSequence s, int start, int before, int count){}
               });

        started = false;
        maxCounter.setText(PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getInt(MAX_COUNT_PREF, defaultMaxCount) + "");


        // Get an instance of the sensor service, and use that to get an instance of
        // a particular sensor.
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (proximitySensor == null) {
            outTextView.setText(getString(R.string.no_sensor_warning));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int change) {
    }

    /** Called when the sensor detects a change in it's reading */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if ((event.sensor.getType() == Sensor.TYPE_PROXIMITY) && started
                && ((event.timestamp - previousTimestamp) > timeThreshold) && (event.values[0] < 1)) {
            outTextView.setText(getString(R.string.count) + ":" + ++currentCounter + "\n"
                    + getString(R.string.reps_completed) + ": " + repsCompleted);
            previousTimestamp = event.timestamp;
            if (currentCounter >= counterMax) {
                if (autoRestartCheckBox.isChecked()) {
                    // restart automatically
                    repsCompleted++;
                    clearCount();
                } else {
                    // stop count
                    toggleCounting();
                }
                playNotification(getApplicationContext());
            }
        }
    }

    /** Plays a sound notification to alert the user. */
    public static void playNotification(Context context) {
        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        Ringtone r = RingtoneManager.getRingtone(context, notification);
        r.play();
    }

    @Override
    protected void onResume() {
        // Register a listener for the sensor.
        super.onResume();
        if ((sensorManager != null) && (proximitySensor != null)) {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        // Be sure to unregister the sensor when the activity pauses.
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onDestroy() {
        //Save changes in UI in preferences
        SharedPreferences.Editor editor =
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit();
        editor.putInt(MAX_COUNT_PREF, getMaxCounterText());
        editor.putBoolean(AUTO_RESTART_PREF, autoRestartCheckBox.isChecked());
        editor.commit();

        super.onDestroy();
    }

    /** Called when the Start button is clicked. */
    public void toggleCounting() {
        hideSoftKeyboard(this, maxCounter);
        started = !started;
        if (started) {
            startButton.setText(getString(R.string.stop));
        } else {
            startButton.setText(getString(R.string.start));
        }
        clearCount();
    }

    /** Called when the Clear button is clicked. Defaults the current count to zero. */
    public void clearRepsAndCount() {
        repsCompleted = 0;
        clearCount();
    }

    public void clearCount() {
        currentCounter = 0;
        outTextView.setText(getString(R.string.count) + ":" + currentCounter + "\n"
                + getString(R.string.reps_completed) + ": " + repsCompleted);
    }

    /** Returns the number in the EditText with id countLimit
     * @return
     */
    private int getMaxCounterText(){
       try {
               return Integer.valueOf(maxCounter.getText().toString());
       }catch(NumberFormatException e){
               return defaultMaxCount;
       }
    }

    public static void hideSoftKeyboard(Activity activity, EditText input) {
        if ((activity.getCurrentFocus() != null) && (activity.getCurrentFocus() instanceof EditText)) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }
}
