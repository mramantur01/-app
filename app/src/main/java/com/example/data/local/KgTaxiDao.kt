package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KgTaxiDao {
    @Query("SELECT * FROM local_profile WHERE id = 1")
    fun observeProfile(): Flow<LocalProfileEntity?>

    @Query("SELECT * FROM local_profile WHERE id = 1")
    suspend fun getProfileOnce(): LocalProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: LocalProfileEntity)

    @Query("SELECT * FROM local_trips ORDER BY createdAtMillis DESC")
    fun observeTrips(): Flow<List<LocalTripEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrip(trip: LocalTripEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrips(trips: List<LocalTripEntity>)

    @Query("DELETE FROM local_trips WHERE id = :id")
    suspend fun deleteTrip(id: String)

    @Query("SELECT * FROM local_saved_places ORDER BY category ASC, label ASC")
    fun observeSavedPlaces(): Flow<List<LocalSavedPlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedPlace(place: LocalSavedPlaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedPlaces(places: List<LocalSavedPlaceEntity>)

    @Query("DELETE FROM local_saved_places WHERE id = :id")
    suspend fun deleteSavedPlace(id: String)

    @Query("SELECT * FROM local_family_members ORDER BY name ASC")
    fun observeFamilyMembers(): Flow<List<LocalFamilyMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFamilyMember(member: LocalFamilyMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFamilyMembers(members: List<LocalFamilyMemberEntity>)

    @Query("DELETE FROM local_family_members WHERE id = :id")
    suspend fun deleteFamilyMember(id: String)

    @Query("SELECT * FROM local_maintenance ORDER BY completed ASC, dueMileageKm ASC")
    fun observeMaintenance(): Flow<List<LocalMaintenanceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMaintenance(item: LocalMaintenanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMaintenanceAll(items: List<LocalMaintenanceEntity>)

    @Query("DELETE FROM local_maintenance WHERE id = :id")
    suspend fun deleteMaintenance(id: String)
}
