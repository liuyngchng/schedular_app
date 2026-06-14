package com.rd.applauncher.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rd.applauncher.model.Schedule
import com.rd.applauncher.model.TimeSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "schedule")

class ScheduleRepository(private val context: Context) {

    val schedule: Flow<Schedule?> = context.dataStore.data.map { prefs ->
        val pkg = prefs[KEY_PACKAGE_NAME] ?: return@map null
        Schedule(
            packageName = pkg,
            appName = prefs[KEY_APP_NAME] ?: "",
            slot1 = TimeSlot(
                startHour = prefs[KEY_S1_START_H] ?: 9,
                startMinute = prefs[KEY_S1_START_M] ?: 0
            ),
            slot2 = TimeSlot(
                startHour = prefs[KEY_S2_START_H] ?: 14,
                startMinute = prefs[KEY_S2_START_M] ?: 0
            ),
            enabled = prefs[KEY_ENABLED] ?: false
        )
    }

    suspend fun save(schedule: Schedule) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PACKAGE_NAME] = schedule.packageName
            prefs[KEY_APP_NAME] = schedule.appName
            prefs[KEY_S1_START_H] = schedule.slot1.startHour
            prefs[KEY_S1_START_M] = schedule.slot1.startMinute
            prefs[KEY_S2_START_H] = schedule.slot2.startHour
            prefs[KEY_S2_START_M] = schedule.slot2.startMinute
            prefs[KEY_ENABLED] = schedule.enabled
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENABLED] = enabled
        }
    }

    companion object {
        private val KEY_PACKAGE_NAME = stringPreferencesKey("package_name")
        private val KEY_APP_NAME = stringPreferencesKey("app_name")
        private val KEY_S1_START_H = intPreferencesKey("s1_start_h")
        private val KEY_S1_START_M = intPreferencesKey("s1_start_m")
        private val KEY_S2_START_H = intPreferencesKey("s2_start_h")
        private val KEY_S2_START_M = intPreferencesKey("s2_start_m")
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
    }
}
