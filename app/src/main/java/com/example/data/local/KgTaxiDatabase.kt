package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LocalProfileEntity::class,
        LocalTripEntity::class,
        LocalSavedPlaceEntity::class,
        LocalFamilyMemberEntity::class,
        LocalMaintenanceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KgTaxiDatabase : RoomDatabase() {
    abstract fun dao(): KgTaxiDao

    companion object {
        @Volatile
        private var INSTANCE: KgTaxiDatabase? = null

        fun getInstance(context: Context): KgTaxiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KgTaxiDatabase::class.java,
                    "kg_taxi_local.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { INSTANCE = it }
            }
        }
    }
}
