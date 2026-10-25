package com.nexabrowser.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nexabrowser.app.data.local.entity.RecentTabEntity

@Dao
interface RecentTabDao {

    @Query("SELECT * FROM recent_tabs ORDER BY position ASC")
    suspend fun getAll(): List<RecentTabEntity>

    @Insert
    suspend fun insertAll(tabs: List<RecentTabEntity>)

    @Query("DELETE FROM recent_tabs")
    suspend fun clearAll()
}
