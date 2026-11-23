package com.example

import com.example.data.model.GeoPoint
import com.example.data.model.OwnerBankConfig
import com.example.data.model.ServiceType
import com.example.util.EmergencyHelper
import com.example.util.OwnerPayoutGatewayClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyAndFlagsUnitTest {

    @Test
    fun sosMessage_containsLocationLinkAndEmergencyNumber() {
        val msg = EmergencyHelper.buildSosMessage(
            passengerName = "Айбек",
            point = GeoPoint(42.8764, 74.6037),
            address = "пр. Чуй 155, Бишкек",
            carInfo = "Toyota Camry • 01KG 123 ABC",
            driverName = "Нурлан"
        )
        assertTrue(msg.contains("https://maps.google.com/?q=42.876400,74.603700"))
        assertTrue(msg.contains("112"))
        assertTrue(msg.contains("Toyota Camry"))
        assertTrue(msg.contains("Айбек"))
    }

    @Test
    fun sosMessage_worksWithoutNameAndRide() {
        val msg = EmergencyHelper.buildSosMessage("", GeoPoint(40.53, 72.80), "Ош", null, null)
        assertTrue(msg.contains("Пассажир KG Taxi"))
        assertFalse(msg.contains("Машина:"))
        assertFalse(msg.contains("Водитель:"))
    }

    @Test
    fun emergencyNumberIs112() {
        assertEquals("112", EmergencyHelper.EMERGENCY_NUMBER)
    }

    @Test
    fun launchServices_areTaxiAndIntercityOnly() {
        assertTrue(FeatureFlags.isServiceEnabled(ServiceType.TAXI))
        assertTrue(FeatureFlags.isServiceEnabled(ServiceType.INTERCITY))
        assertFalse(FeatureFlags.isServiceEnabled(ServiceType.FOOD))
        assertFalse(FeatureFlags.isServiceEnabled(ServiceType.CARGO))
    }

    @Test
    fun payoutGateway_isNeverLiveInTheApp() = runTest {
        assertFalse(OwnerPayoutGatewayClient.hasLiveGatewayConfigured())
        val result = OwnerPayoutGatewayClient.executeOwnerSettlement(
            config = OwnerBankConfig(),
            amountSom = 500,
            referenceCode = "REF1",
            orderId = null,
            offlineMode = false
        )
        assertFalse(result.deliveredViaLiveGateway)
        assertTrue(result.statusMessage.contains("НЕ отправлены"))
    }
}
