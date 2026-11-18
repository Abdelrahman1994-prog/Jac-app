package com.jacdrive.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleProfileDao {
    @Query("SELECT * FROM vehicle_profile WHERE id = :id")
    fun observe(id: Long = VehicleProfileEntity.PRIMARY_ID): Flow<VehicleProfileEntity?>

    @Query("SELECT * FROM vehicle_profile WHERE id = :id")
    suspend fun get(id: Long = VehicleProfileEntity.PRIMARY_ID): VehicleProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: VehicleProfileEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(profile: VehicleProfileEntity)
}
