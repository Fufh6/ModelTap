package com.wuyousheng.modeltap.storage

import android.content.SharedPreferences

class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String?, defValue: String?): String? {
        return values[key] ?: defValue
    }

    override fun edit(): SharedPreferences.Editor {
        return Editor()
    }

    private inner class Editor : SharedPreferences.Editor {
        private val updates = mutableMapOf<String, String?>()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) updates[key] = value
            return this
        }

        override fun apply() {
            commit()
        }

        override fun commit(): Boolean {
            updates.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
            return true
        }

        override fun remove(key: String?): SharedPreferences.Editor = this
        override fun clear(): SharedPreferences.Editor = this
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
    }

    override fun getAll(): MutableMap<String, *> = values
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String?, defValue: Int): Int = defValue
    override fun getLong(key: String?, defValue: Long): Long = defValue
    override fun getFloat(key: String?, defValue: Float): Float = defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}
