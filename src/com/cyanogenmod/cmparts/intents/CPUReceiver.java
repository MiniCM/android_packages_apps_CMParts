/*
 * Copyright (C) 2011 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cyanogenmod.cmparts.intents;

import com.cyanogenmod.cmparts.activities.CPUActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import android.provider.Settings;
import java.io.DataOutputStream;
import java.io.DataInputStream;

public class CPUReceiver extends BroadcastReceiver {

    private static final String TAG = "CPUSettings";

    private static final String CPU_SETTINGS_PROP = "sys.cpufreq.restored";
    
    private static final String ULTRA_BRIGHTNESS_PROP = "persist.sys.ultrabrightness";
    
    private static final String OVERCLOCKING_PROP = "persist.sys.overclock";
    
    private static String OC_MODULE;
        
    private static final String UNDERVOLTING_PROP = "persist.sys.undervolt";

    private static String UV_MODULE;
    
    private static String AVAILABLE_FREQUENCIES_FILE = "/data/local/tmp/available_frequencies";
    
    private static String OVERCLOCKED_FREQUENCIES = "122880 245760 320000 480000 600000 614400 633600 652800 672000 691200 710400 729600 748800 768000 787200 806400 825600";

    private static String NON_OVERCLOCKED_FREQUENCIES = "122880 245760 320000 480000 600000";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (SystemProperties.getBoolean(CPU_SETTINGS_PROP, false) == false
                && intent.getAction().equals(Intent.ACTION_MEDIA_MOUNTED)) {
            SystemProperties.set(CPU_SETTINGS_PROP, "true");
            configureCPU(ctx);
        } else {
            SystemProperties.set(CPU_SETTINGS_PROP, "false");
        }
        
        if (SystemProperties.getBoolean(ULTRA_BRIGHTNESS_PROP, false) == true) {
            writeOneLine("/sys/devices/platform/i2c-adapter/i2c-0/0-0036/mode", "i2c_pwm");
            Log.e(TAG, "Ultra Brightness writing i2c_pwm: ");
        }   
        else {
            writeOneLine("/sys/devices/platform/i2c-adapter/i2c-0/0-0036/mode", "i2c_pwm_als");
            Log.e(TAG, "Ultra Brightness writing i2c_pwm_als: ");
	}
	
    }

    private void configureCPU(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
	
        if (prefs.getBoolean(CPUActivity.SOB_PREF, false) == false) {
	    SystemProperties.set(UNDERVOLTING_PROP, "0");
	    SystemProperties.set(OVERCLOCKING_PROP, "0");
            Log.i(TAG, "Restore disabled by user preference.");
            return;
        }

	OC_MODULE = ctx.getResources().getString(com.cyanogenmod.cmparts.R.string.overclocking_module);
	if (SystemProperties.getBoolean(OVERCLOCKING_PROP, false) == true) {
            // insmod overclocking module
            insmod(OC_MODULE, true);
            // also update the available frequencies
            CPUActivity.writeOneLine(AVAILABLE_FREQUENCIES_FILE, OVERCLOCKED_FREQUENCIES);
            
        }   
        else {
            // remove overclocking module
            //insmod(UV_MODULE, false);
            // also update the available frequencies
            CPUActivity.writeOneLine(AVAILABLE_FREQUENCIES_FILE, NON_OVERCLOCKED_FREQUENCIES);
	}
	        
        UV_MODULE = ctx.getResources().getString(com.cyanogenmod.cmparts.R.string.undervolting_module);
	if (SystemProperties.getBoolean(UNDERVOLTING_PROP, false) == true) {
            // insmod undervolting module
            insmod(UV_MODULE, true);
        }   
        else {
            // remove undervolting module
            //insmod(UV_MODULE, false);
	}

        String governor = prefs.getString(CPUActivity.GOV_PREF, null);
        String minFrequency = prefs.getString(CPUActivity.MIN_FREQ_PREF, null);
        String maxFrequency = prefs.getString(CPUActivity.MAX_FREQ_PREF, null);
        String availableFrequenciesLine = CPUActivity.readOneLine(CPUActivity.FREQ_LIST_FILE);
        String availableGovernorsLine = CPUActivity.readOneLine(CPUActivity.GOVERNORS_LIST_FILE);
        boolean noSettings = ((availableGovernorsLine == null) || (governor == null)) && 
                             ((availableFrequenciesLine == null) || ((minFrequency == null) && (maxFrequency == null)));
        List<String> frequencies = null;
        List<String> governors = null;
        
        if (noSettings) {
            Log.d(TAG, "No settings saved. Nothing to restore.");
        } else {
            if (availableGovernorsLine != null){
                governors = Arrays.asList(availableGovernorsLine.split(" "));  
            }
            if (availableFrequenciesLine != null){
                frequencies = Arrays.asList(availableFrequenciesLine.split(" "));  
            }
            if (governor != null && governors != null && governors.contains(governor)) {
                CPUActivity.writeOneLine(CPUActivity.GOVERNOR, governor);
            }
            if (maxFrequency != null && frequencies != null && frequencies.contains(maxFrequency)) {
                CPUActivity.writeOneLine(CPUActivity.FREQ_MAX_FILE, maxFrequency);
            }
            if (minFrequency != null && frequencies != null && frequencies.contains(minFrequency)) {
                CPUActivity.writeOneLine(CPUActivity.FREQ_MIN_FILE, minFrequency);
            }
            Log.d(TAG, "CPU settings restored.");
        }
    }
    
    public static boolean writeOneLine(String fname, String value) {
        try {
            FileWriter fw = new FileWriter(fname);
            try {
                fw.write(value);
            } finally {
                fw.close();
            }
        } catch (IOException e) {
            String Error = "Error writing to " + fname + ". Exception: ";
            Log.e(TAG, Error, e);
            return false;
        }
        return true;
    }
    
    private static boolean insmod(String module, boolean insert) {
    	String command;
	if (insert)
		command = "/system/bin/insmod /system/lib/modules/" + module;
	else
		command = "/system/bin/rmmod " + module;
    	    try
	    {
		Process process = Runtime.getRuntime().exec("su");
		Log.e(TAG, "Executing: " + command);
		DataOutputStream outputStream = new DataOutputStream(process.getOutputStream()); 
		DataInputStream inputStream = new DataInputStream(process.getInputStream());
		outputStream.writeBytes(command + "\n");
		outputStream.flush();
		outputStream.writeBytes("exit\n"); 
		outputStream.flush(); 
		process.waitFor();
	    }
	    catch (IOException e)
	    {
		return false;
	    }
	    catch (InterruptedException e)
	    {
		return false;
	    }
        return true;
    }
}
