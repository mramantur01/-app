package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_profile")
data class LocalProfileEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String = "Амантур",
    val phone: String = "+996 555 010 996",
    val email: String = "",
    val homeAddress: String = "пр. Чуй 155, Бишкек",
    val workAddress: String = "ТРЦ Asia Mall, пр. Ч. Айтматова 3",
    val paymentMethod: String = "MBANK",
    val kgOneActive: Boolean = false,
    val businessProfileActive: Boolean = false,
    val companyName: String = "Ala-Too Tech",
    val companyEmail: String = "finance@alatoo.kg",
    val bonusPoints: Int = 250,
    val preferredCity: String = "Бишкек",
    val language: String = "RU",
    val darkTheme: Boolean = false,
    val soundEnabled: Boolean = true,
    val preferredTariffId: String = "comfort",
    val tariffUsageCounts: String = "comfort:3,econom:2,green_ev:1"
)

@Entity(tableName = "local_trips")
data class LocalTripEntity(
    @PrimaryKey val id: String,
    val serviceType: String,
    val tariffName: String,
    val city: String,
    val pickupAddress: String,
    val dropoffAddress: String,
    val distanceKm: Double,
    val durationMin: Int,
    val totalPriceSom: Int,
    val paymentMethod: String,
    val status: String,
    val driverName: String,
    val driverCar: String,
    val safetyPin: String,
    val rating: Int,
    val tipSom: Int,
    val businessTrip: Boolean,
    val lostItemNote: String,
    val createdAtMillis: Long
)

@Entity(tableName = "local_saved_places")
data class LocalSavedPlaceEntity(
    @PrimaryKey val id: String,
    val label: String,
    val address: String,
    val city: String,
    val lat: Double,
    val lng: Double,
    val category: String,
    val rating: Double
)

@Entity(tableName = "local_family_members")
data class LocalFamilyMemberEntity(
    @PrimaryKey val id: String,
    val name: String,
    val relation: String,
    val phone: String,
    val monthlyLimitSom: Int,
    val teenControlEnabled: Boolean
)

@Entity(tableName = "local_maintenance")
data class LocalMaintenanceEntity(
    @PrimaryKey val id: String,
    val vehicleName: String,
    val serviceTitle: String,
    val dueMileageKm: Int,
    val currentMileageKm: Int,
    val fuelLitersPer100Km: Double,
    val completed: Boolean
)
