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

package com.cyanogenmod.cmparts.activities;

import com.cyanogenmod.cmparts.R;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.preference.CheckBoxPreference;
import android.os.SystemProperties;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.DataInputStream;

//
// CPU Related Settings
//
public class CPUActivity extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener {

    public static final String GOV_PREF = "pref_cpu_gov";
    public static final String GOVERNORS_LIST_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors";
    public static final String GOVERNOR = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor";
    public static final String MIN_FREQ_PREF = "pref_freq_min";
    public static final String MAX_FREQ_PREF = "pref_freq_max";
    public static final String FREQ_LIST_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies";
    public static final String FREQ_LIST_FILE2 = "/data/local/tmp/available_frequencies";
    public static final String FREQ_MAX_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq";
    public static final String FREQ_MIN_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq";
    public static final String SOB_PREF = "pref_set_on_boot";

    private static final String TAG = "CPUSettings";

    private String mGovernorFormat;
    private String mMinFrequencyFormat;
    private String mMaxFrequencyFormat;

    private ListPreference mGovernorPref;
    private ListPreference mMinFrequencyPref;
    private ListPreference mMaxFrequencyPref;
    
    private static String UV_MODULE;
    private static final String UNDERVOLT = "pref_undervolt";
    private static final String UNDERVOLT_PROP = "sys.undervolt";
    private static final String UNDERVOLT_PERSIST_PROP = "persist.sys.undervolt";
    private static final int UNDERVOLT_DEFAULT = 0; 
    private CheckBoxPreference mUndervoltPref;
    
    private static String OC_MODULE;
    private static final String OVERCLOCK = "pref_overclock";
    private static final String OVERCLOCK_PROP = "sys.overclock";
    private static final String OVERCLOCK_PERSIST_PROP = "persist.sys.overclock";
    private static final int OVERCLOCK_DEFAULT = 0; 
    private CheckBoxPreference mOverclockPref;

    private static String AVAILABLE_FREQUENCIES_FILE = "/data/local/tmp/available_frequencies";
    
    private static String OVERCLOCKED_FREQUENCIES = "122880 245760 320000 480000 600000 614400 633600 652800 672000 691200 710400 729600 748800 768000 787200 806400 825600";

    private static String NON_OVERCLOCKED_FREQUENCIES = "122880 245760 320000 480000 600000";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mGovernorFormat = getString(R.string.cpu_governors_summary);
        mMinFrequencyFormat = getString(R.string.cpu_min_freq_summary);
        mMaxFrequencyFormat = getString(R.string.cpu_max_freq_summary);

        String[] availableGovernors = readOneLine(GOVERNORS_LIST_FILE).split(" ");
        String[] availableFrequencies = new String[0];
        String availableFrequenciesLine = readOneLine(FREQ_LIST_FILE);
	Log.e(TAG, "I read:: " + availableFrequenciesLine);
        if (availableFrequenciesLine != null)
             availableFrequencies = availableFrequenciesLine.split(" ");
        String[] frequencies;
        String temp;

        frequencies = new String[availableFrequencies.length];
        for (int i = 0; i < frequencies.length; i++) {
            frequencies[i] = toMHz(availableFrequencies[i]);
        }

        setTitle(R.string.cpu_title);
        addPreferencesFromResource(R.xml.cpu_settings);

        PreferenceScreen PrefScreen = getPreferenceScreen();

        temp = readOneLine(GOVERNOR);

        mGovernorPref = (ListPreference) PrefScreen.findPreference(GOV_PREF);
        mGovernorPref.setEntryValues(availableGovernors);
        mGovernorPref.setEntries(availableGovernors);
        mGovernorPref.setValue(temp);
        mGovernorPref.setSummary(String.format(mGovernorFormat, temp));
        mGovernorPref.setOnPreferenceChangeListener(this);

        /* Some systems might not use governors */
        if (temp == null) {
            PrefScreen.removePreference(mGovernorPref);
        }

        temp = readOneLine(FREQ_MIN_FILE);

        mMinFrequencyPref = (ListPreference) PrefScreen.findPreference(MIN_FREQ_PREF);
        mMinFrequencyPref.setEntryValues(availableFrequencies);
        mMinFrequencyPref.setEntries(frequencies);
        mMinFrequencyPref.setValue(temp);
        mMinFrequencyPref.setSummary(String.format(mMinFrequencyFormat, toMHz(temp)));
        mMinFrequencyPref.setOnPreferenceChangeListener(this);

        temp = readOneLine(FREQ_MAX_FILE);

        mMaxFrequencyPref = (ListPreference) PrefScreen.findPreference(MAX_FREQ_PREF);
        mMaxFrequencyPref.setEntryValues(availableFrequencies);
        mMaxFrequencyPref.setEntries(frequencies);
        mMaxFrequencyPref.setValue(temp);
        mMaxFrequencyPref.setSummary(String.format(mMaxFrequencyFormat, toMHz(temp)));
        mMaxFrequencyPref.setOnPreferenceChangeListener(this);
        
        /* Overclocking */
        mOverclockPref = (CheckBoxPreference) PrefScreen.findPreference(OVERCLOCK);
        mOverclockPref.setOnPreferenceChangeListener(this);
        if (SystemProperties.getInt(OVERCLOCK_PERSIST_PROP, OVERCLOCK_DEFAULT) == 0) {
		mOverclockPref.setChecked(false);
		}
	else {
		mOverclockPref.setChecked(true);
		
		}
        
        /* Undervolting */
        mUndervoltPref = (CheckBoxPreference) PrefScreen.findPreference(UNDERVOLT);
        //mUndervoltPref.setDependency(OVERCLOCK);
        mUndervoltPref.setOnPreferenceChangeListener(this);
        if (SystemProperties.getInt(UNDERVOLT_PERSIST_PROP, UNDERVOLT_DEFAULT) == 0)
		mUndervoltPref.setChecked(false);
	else
		mUndervoltPref.setChecked(true);
        if (mOverclockPref.isChecked()==true) {
        	mUndervoltPref.setEnabled(false);
        }
        if (mUndervoltPref.isChecked()==true) {
        	mOverclockPref.setEnabled(false);
        }
        	
    }

    @Override
    public void onResume() {
        String temp;

        super.onResume();

        temp = readOneLine(FREQ_MAX_FILE);
        mMaxFrequencyPref.setValue(temp);
        mMaxFrequencyPref.setSummary(String.format(mMaxFrequencyFormat, toMHz(temp)));

        temp = readOneLine(FREQ_MIN_FILE);
        mMinFrequencyPref.setValue(temp);
        mMinFrequencyPref.setSummary(String.format(mMinFrequencyFormat, toMHz(temp)));

        temp = readOneLine(GOVERNOR);
        mGovernorPref.setSummary(String.format(mGovernorFormat, temp));
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String fname = "";
	boolean value;
        if (newValue != null) {
            if (preference == mOverclockPref) {
        	OC_MODULE = getResources().getString(R.string.overclocking_module);
                value = mOverclockPref.isChecked();
                if (value==true) {
		        SystemProperties.set(OVERCLOCK_PERSIST_PROP, "0");
		        mUndervoltPref.setEnabled(true);
			//remove the overclocking module
			//insmod(OV_MODULE, false);
			// also update the available frequencies
            		writeOneLine(AVAILABLE_FREQUENCIES_FILE, NON_OVERCLOCKED_FREQUENCIES);
            		String[] availableFrequencies = new String[0];
			String availableFrequenciesLine = readOneLine(AVAILABLE_FREQUENCIES_FILE);
			Log.e(TAG, "I read:: " + availableFrequenciesLine);
			if (availableFrequenciesLine != null)
			     availableFrequencies = availableFrequenciesLine.split(" ");
			String[] frequencies;
			String temp;

			frequencies = new String[availableFrequencies.length];
			for (int i = 0; i < frequencies.length; i++) {
			    frequencies[i] = toMHz(availableFrequencies[i]);
			}
            		mMinFrequencyPref.setEntryValues(availableFrequencies);
            		mMinFrequencyPref.setEntries(frequencies);
            		mMaxFrequencyPref.setEntryValues(availableFrequencies);
            		mMaxFrequencyPref.setEntries(frequencies);
                }
                else {
		    	SystemProperties.set(OVERCLOCK_PERSIST_PROP, "1");
	                // Disable undervolting
	                mUndervoltPref.setChecked(false);
	                SystemProperties.set(UNDERVOLT_PERSIST_PROP, "0");
	                mUndervoltPref.setEnabled(false);
			insmod(UV_MODULE, false);
			
			//insmod the overclocking module
			insmod(OC_MODULE, true);
			// also update the available frequencies
            		writeOneLine(AVAILABLE_FREQUENCIES_FILE, OVERCLOCKED_FREQUENCIES);
            		String[] availableFrequencies = new String[0];
			String availableFrequenciesLine = readOneLine(AVAILABLE_FREQUENCIES_FILE);
			Log.e(TAG, "I read:: " + availableFrequenciesLine);
			if (availableFrequenciesLine != null)
			     availableFrequencies = availableFrequenciesLine.split(" ");
			String[] frequencies;
			String temp;

			frequencies = new String[availableFrequencies.length];
			for (int i = 0; i < frequencies.length; i++) {
			    frequencies[i] = toMHz(availableFrequencies[i]);
			}
            		mMinFrequencyPref.setEntryValues(availableFrequencies);
            		mMinFrequencyPref.setEntries(frequencies);
            		mMaxFrequencyPref.setEntryValues(availableFrequencies);
            		mMaxFrequencyPref.setEntries(frequencies);
                }
		return true;
            }
            if (preference == mUndervoltPref) {
        	UV_MODULE = getResources().getString(R.string.undervolting_module);
                value = mUndervoltPref.isChecked();
                if (value==true) {
		    SystemProperties.set(UNDERVOLT_PERSIST_PROP, "0");
		    mOverclockPref.setEnabled(true);
		    //remove the undervolting module
		    insmod(UV_MODULE, false);
                }
                else {
		    SystemProperties.set(UNDERVOLT_PERSIST_PROP, "1");
		    mOverclockPref.setEnabled(false);
		    //insmod the undervolting module
		    insmod(UV_MODULE, true);
                }
		return true;
            }
            if (preference == mGovernorPref) {
                fname = GOVERNOR;
            } else if (preference == mMinFrequencyPref) {
                fname = FREQ_MIN_FILE;
            } else if (preference == mMaxFrequencyPref) {
                fname = FREQ_MAX_FILE;
            }

            if (writeOneLine(fname, (String) newValue)) {
                if (preference == mGovernorPref) {
                    mGovernorPref.setSummary(String.format(mGovernorFormat, (String) newValue));
                } else if (preference == mMinFrequencyPref) {
                    mMinFrequencyPref.setSummary(String.format(mMinFrequencyFormat,
                            toMHz((String) newValue)));
                } else if (preference == mMaxFrequencyPref) {
                    mMaxFrequencyPref.setSummary(String.format(mMaxFrequencyFormat,
                            toMHz((String) newValue)));
                }
                return true;
            } else {
                return false;
            }
            
        }
        return false;
    }

    public static String readOneLine(String fname) {
        BufferedReader br;
        String line = null;

        try {
            br = new BufferedReader(new FileReader(fname), 512);
            try {
                line = br.readLine();
            } finally {
                br.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "IO Exception when reading /sys/ file", e);
            if ( fname.endsWith("scaling_available_frequencies") ) {
		Log.e(TAG, "Using " + FREQ_LIST_FILE2 + " instead");
                return readOneLine(FREQ_LIST_FILE2);
            }
        }
        return line;
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

    private String toMHz(String mhzString) {
        return new StringBuilder().append(Integer.valueOf(mhzString) / 1000).append(" MHz").toString();
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
 
