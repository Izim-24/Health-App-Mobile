package com.healthapp.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.healthapp.data.local.dao.AccountDao
import com.healthapp.data.local.entity.AccountEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "account_prefs")

@Singleton
class AccountManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountDao: AccountDao
) : com.healthapp.domain.repository.AccountSession {
    companion object {
        private val CURRENT_ACCOUNT_ID = stringPreferencesKey("current_account_id")
        private val DEFAULT_ACCOUNT_ID = "local_default_account"
    }

    val currentAccountId: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[CURRENT_ACCOUNT_ID] ?: ""
        }

    override suspend fun getCurrentAccountId(): String {
        val stored = context.dataStore.data.first()[CURRENT_ACCOUNT_ID]
        if (stored != null && stored.isNotEmpty()) {
            return stored
        }
        // Create default account if none exists
        return createDefaultAccount()
    }

    private suspend fun createDefaultAccount(): String {
        // Check if default account exists
        val existingAccount = accountDao.getAccountById(DEFAULT_ACCOUNT_ID)
        if (existingAccount != null) {
            saveCurrentAccountId(DEFAULT_ACCOUNT_ID)
            return DEFAULT_ACCOUNT_ID
        }

        // Create new default account
        val now = LocalDateTime.now()
        val defaultAccount = AccountEntity(
            id = DEFAULT_ACCOUNT_ID,
            email = "local@healthapp.local",
            passwordHash = "", // Local account, no password
            createdAt = now,
            updatedAt = now
        )
        accountDao.insertAccount(defaultAccount)
        saveCurrentAccountId(DEFAULT_ACCOUNT_ID)
        return DEFAULT_ACCOUNT_ID
    }

    private suspend fun saveCurrentAccountId(accountId: String) {
        context.dataStore.edit { preferences ->
            preferences[CURRENT_ACCOUNT_ID] = accountId
        }
    }

    suspend fun isLoggedIn(): Boolean {
        val accountId = getCurrentAccountId()
        return accountId.isNotEmpty()
    }
}