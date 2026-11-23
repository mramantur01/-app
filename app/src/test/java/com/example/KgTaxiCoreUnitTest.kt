package com.example

import com.example.data.local.LocalProfileEntity
import com.example.data.model.GeoPoint
import com.example.data.model.KgGeoCatalog
import com.example.data.model.KgPlace
import com.example.data.model.PickupSpeed
import com.example.data.model.ServiceType
import com.example.ui.KgTaxiUiState
import com.example.util.KyrgyzstanRoutingClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KgTaxiCoreUnitTest {

    @Test
    fun `all 8 Kyrgyzstan cities are registered with landmarks and surge zones`() {
        val names = KgGeoCatalog.cities.map { it.name }
        val expected = listOf("Бишкек", "Ош", "Чолпон-Ата", "Каракол", "Джалал-Абад", "Талас", "Нарын", "Баткен")
        assertEquals(8, names.size)
        expected.forEach { city ->
            assertTrue("Missing city $city", city in names)
        }
        KgGeoCatalog.cities.forEach { c ->
            assertTrue("${c.name} should have landmarks", c.landmarks.isNotEmpty())
            assertTrue("${c.name} should have surge zones", c.surgeZones.isNotEmpty())
        }
    }

    @Test
    fun `osrm json parser extracts road polyline distance and duration accurately`() {
        val sampleJson = """
            {
              "code": "Ok",
              "routes": [
                {
                  "distance": 4620.5,
                  "duration": 715.2,
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      [74.6037, 42.8764],
                      [74.5950, 42.8660],
                      [74.5855, 42.8559]
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = KyrgyzstanRoutingClient.parseOsrmResponse(sampleJson)
        assertNotNull(parsed)
        assertEquals(3, parsed!!.polyline.size)
        assertEquals(42.8764, parsed.polyline.first().lat, 0.0001)
        assertEquals(74.6037, parsed.polyline.first().lng, 0.0001)
        assertEquals(4.6, parsed.distanceKm, 0.05)
        assertEquals(12, parsed.durationMin)
        assertTrue(parsed.fromLiveOsrm)
    }

    @Test
    fun `pricing engine computes wait and save, priority, kg one, promo, bonus and split fare`() {
        val baseState = KgTaxiUiState(
            serviceType = ServiceType.TAXI,
            selectedTariffId = "comfort",
            liveRouteDistanceKm = 10.0,
            liveRouteDurationMin = 20,
            selectedCity = KgGeoCatalog.cityByName("Ош").copy(surgeMultiplier = 1.0),
            pickupSpeed = PickupSpeed.STANDARD
        )
        val standardTotal = baseState.priceBreakdown.finalTotalSom

        val waitSaveTotal = baseState.copy(pickupSpeed = PickupSpeed.WAIT_AND_SAVE).priceBreakdown.finalTotalSom
        assertTrue("Wait & Save should be cheaper than Standard", waitSaveTotal < standardTotal)

        val priorityTotal = baseState.copy(pickupSpeed = PickupSpeed.PRIORITY).priceBreakdown.finalTotalSom
        assertEquals(standardTotal + 30, priorityTotal)

        val discounted = baseState.copy(
            profile = LocalProfileEntity(kgOneActive = true, bonusPoints = 200),
            appliedPromoPercent = 15,
            useBonusPoints = true,
            splitCount = 3
        ).priceBreakdown
        // KG One is a paid subscription: its discount applies only when the wallet is switched on.
        if (FeatureFlags.WALLET) {
            assertTrue("KG One discount applied", discounted.kgOneDiscountSom > 0)
        } else {
            assertEquals("No KG One discount without wallet", 0, discounted.kgOneDiscountSom)
        }
        assertTrue("Promo discount applied", discounted.promoDiscountSom > 0)
        assertTrue("Bonus points deducted", discounted.bonusUsedSom > 0)
        assertTrue("Discounted total < standard", discounted.finalTotalSom < standardTotal)
        assertEquals((discounted.finalTotalSom + 2) / 3, discounted.perPersonSom)
    }

    @Test
    fun `intercity cabin seat pricing includes front seat extra and hourly is 650 per hour`() {
        val route = KgGeoCatalog.intercityRoutes.first()
        val intercity = KgTaxiUiState(
            serviceType = ServiceType.INTERCITY,
            selectedIntercityRoute = route,
            selectedIntercitySeats = setOf(1, 3)
        ).priceBreakdown
        assertEquals(route.seatPriceSom * 2 + route.frontSeatExtraSom, intercity.baseSom)

        val hourly = KgTaxiUiState(
            serviceType = ServiceType.HOURLY,
            hourlyHours = 4,
            selectedExcursion = null
        ).priceBreakdown
        assertEquals(4 * 650, hourly.baseSom)
    }

    @Test
    fun `reverse geocode fallback formats valid street and city`() {
        val bishkek = KgGeoCatalog.cityByName("Бишкек")
        val addr = KgGeoCatalog.reverseGeocodeFallback(bishkek, GeoPoint(42.8764, 74.6037))
        assertTrue(addr.contains("Бишкек"))
        assertTrue(addr.isNotBlank())
    }

    @Test
    fun `multi-stop waypoints add 45 som per stop and extend offline route legs`() {
        val wp1 = KgPlace("wp1", "ЦУМ Айчурек", "пр. Чуй 155", "Бишкек", GeoPoint(42.8760, 74.6143), "ТРЦ", 4.7, 320, 2)
        val wp2 = KgPlace("wp2", "Ошский базар", "ул. Бейшеналиевой", "Бишкек", GeoPoint(42.8748, 74.5698), "Базар", 4.5, 410, 1)
        val state = KgTaxiUiState(
            serviceType = ServiceType.TAXI,
            selectedTariffId = "comfort",
            liveRouteDistanceKm = 8.0,
            liveRouteDurationMin = 16,
            selectedCity = KgGeoCatalog.cityByName("Ош").copy(surgeMultiplier = 1.0),
            waypoints = listOf(wp1, wp2)
        )
        assertEquals(90, state.priceBreakdown.waypointsSom)

        val directRoute = KyrgyzstanRoutingClient.buildOfflineFallbackRoute(
            pickup = GeoPoint(42.8764, 74.6037),
            dropoff = GeoPoint(42.8559, 74.5855)
        )
        val multiStopRoute = KyrgyzstanRoutingClient.buildOfflineFallbackRoute(
            pickup = GeoPoint(42.8764, 74.6037),
            dropoff = GeoPoint(42.8559, 74.5855),
            waypoints = listOf(wp1.point, wp2.point)
        )
        assertTrue(
            "Multi-stop route should be longer than direct route",
            multiStopRoute.distanceKm > directRoute.distanceKm
        )
        assertTrue(
            "Multi-stop route should contain more polyline points",
            multiStopRoute.polyline.size > directRoute.polyline.size
        )
    }

    @Test
    fun `sober driver tariff and smart pickup spots are properly configured`() {
        val sober = KgGeoCatalog.taxiTariffs.firstOrNull { it.id == "sober_driver" }
        assertNotNull("Sober driver tariff should exist", sober)
        assertTrue(sober!!.soberDriver)
        assertEquals(450, sober.baseSom)

        val manasSpots = KgGeoCatalog.pickupSpotsForAddress("Аэропорт Манас (FRU)")
        assertTrue("Manas airport should have smart pickup spots", manasSpots.isNotEmpty())
        assertTrue(manasSpots.any { it.contains("Выход") || it.contains("VIP") })
    }

    @Test
    fun `referral code uses sanitized display name with KG prefix and 250 suffix`() {
        val state = KgTaxiUiState(
            profile = LocalProfileEntity(displayName = "Timur Aliev")
        )
        assertEquals("KG-TIMU250", state.referralCode)

        val cyrillicState = KgTaxiUiState(
            profile = LocalProfileEntity(displayName = "Бекзат")
        )
        assertEquals("KG-KG250", cyrillicState.referralCode)
    }

    @Test
    fun `open-meteo weather and aqi parser extracts temperature wind aqi and mountain passes`() {
        val weatherJson = """
            {"current":{"temperature_2m":1.4,"wind_speed_10m":12.6,"weather_code":71}}
        """.trimIndent()
        val aqiJson = """
            {"current":{"us_aqi":138,"pm2_5":49.2}}
        """.trimIndent()

        val parsed = com.example.util.KgWeatherAndPassesClient.parseOpenMeteoWeather("Бишкек", weatherJson, aqiJson)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.tempC)
        assertEquals(13, parsed.windKmh)
        assertEquals(138, parsed.aqiUs)
        assertTrue(parsed.isLive)

        val passes = com.example.util.KgWeatherAndPassesClient.buildMountainPasses(parsed)
        assertEquals(4, passes.size)
        assertTrue("Too-Ashuu should flag winter caution at 1C", passes.first { it.id == "too_ashuu" }.isCaution)
    }

    @Test
    fun `voice-to-ride parser extracts pickup dropoff tariff and ride preferences`() {
        val intent = com.example.data.firebase.AiRouteAssistant.parseVoiceOrderOffline(
            "С ЦУМа в аэропорт Манас на Комфорте с кондиционером и багажом"
        )
        assertEquals("ЦУМ Айчурек", intent.pickupQuery)
        assertEquals("Аэропорт Манас (FRU)", intent.dropoffQuery)
        assertEquals("comfort", intent.tariffId)
        assertEquals(true, intent.climateAc)
        assertEquals(true, intent.luggageHelp)
    }

    @Test
    fun `tourist multi-currency formatting and meet and greet pricing work accurately`() {
        val usdText = com.example.data.model.DisplayCurrency.USD.formatSom(875)
        assertTrue("USD conversion should include $10.00, got $usdText", usdText.contains("$10.00"))

        val withMeetGreet = KgTaxiUiState(
            serviceType = ServiceType.TAXI,
            meetAndGreetEnabled = true,
            profile = LocalProfileEntity(kgOneActive = false)
        ).priceBreakdown
        assertEquals(150, withMeetGreet.meetAndGreetSom)

        val freeWithKgOne = KgTaxiUiState(
            serviceType = ServiceType.TAXI,
            meetAndGreetEnabled = true,
            profile = LocalProfileEntity(kgOneActive = true)
        ).priceBreakdown
        assertEquals(if (FeatureFlags.WALLET) 0 else 150, freeWithKgOne.meetAndGreetSom)
    }

    @Test
    fun `carpool share discount and kg plus free priority and surge shield work accurately`() {
        val base = KgTaxiUiState(
            serviceType = ServiceType.TAXI,
            selectedTariffId = "comfort",
            liveRouteDistanceKm = 10.0,
            selectedCity = KgGeoCatalog.cityByName("Бишкек").copy(surgeMultiplier = 1.4),
            pickupSpeed = PickupSpeed.PRIORITY,
            profile = LocalProfileEntity(kgOneActive = false),
            carpoolModeEnabled = false
        ).priceBreakdown

        val withCarpool = KgTaxiUiState(
            serviceType = ServiceType.TAXI,
            selectedTariffId = "comfort",
            liveRouteDistanceKm = 10.0,
            selectedCity = KgGeoCatalog.cityByName("Бишкек").copy(surgeMultiplier = 1.4),
            pickupSpeed = PickupSpeed.PRIORITY,
            profile = LocalProfileEntity(kgOneActive = false),
            carpoolModeEnabled = true
        ).priceBreakdown

        val withKgPlus = KgTaxiUiState(
            serviceType = ServiceType.TAXI,
            selectedTariffId = "comfort",
            liveRouteDistanceKm = 10.0,
            selectedCity = KgGeoCatalog.cityByName("Бишкек").copy(surgeMultiplier = 1.4),
            pickupSpeed = PickupSpeed.PRIORITY,
            profile = LocalProfileEntity(kgOneActive = true),
            carpoolModeEnabled = false
        ).priceBreakdown

        assertTrue("Carpool should grant ~30% discount", withCarpool.carpoolDiscountSom > 0)
        assertTrue("Carpool final price should be cheaper", withCarpool.finalTotalSom < base.finalTotalSom)
        if (FeatureFlags.WALLET) {
            assertEquals("KG Plus makes Priority pickup free", 0, withKgPlus.speedDeltaSom)
            assertTrue("KG Plus halves surge cost", withKgPlus.surgeSom < base.surgeSom)
            assertTrue("KG Plus gives 10% cashback", withKgPlus.kgPlusCashbackSom >= 10)
        } else {
            // Paid perks are off until the subscription can actually be bought.
            assertEquals("No KG Plus perks without wallet", base.finalTotalSom, withKgPlus.finalTotalSom)
        }
    }

    @Test
    fun `hourly demand forecast and intercity live schedule provide structured data`() {
        val bishkek = KgGeoCatalog.cityByName("Бишкек")
        val forecast = KgGeoCatalog.hourlyDemandForecast(bishkek)
        assertEquals(7, forecast.size)
        assertTrue(forecast.any { it.surgeLabel == "x1.8" })

        val schedule = KgGeoCatalog.intercityScheduleForRoute("ic1")
        assertEquals(3, schedule.size)
        schedule.forEach { trip ->
            assertTrue("Trip should have free seats", trip.freeSeatsCount in 1..6)
        }
    }

    @Test
    fun `owner commission split engine calculates exact owner cut and driver net accurately`() {
        val standard = com.example.util.OwnerPayoutGatewayClient.calculateOrderSplit(
            grossSom = 500,
            commissionPercent = 14,
            commissionFreeShiftActive = false
        )
        assertEquals(70, standard.ownerCommissionSom)
        assertEquals(430, standard.driverNetSom)
        assertEquals(14, standard.commissionPercent)

        val customRate = com.example.util.OwnerPayoutGatewayClient.calculateOrderSplit(
            grossSom = 1000,
            commissionPercent = 20,
            commissionFreeShiftActive = false
        )
        assertEquals(200, customRate.ownerCommissionSom)
        assertEquals(800, customRate.driverNetSom)

        val shiftFree = com.example.util.OwnerPayoutGatewayClient.calculateOrderSplit(
            grossSom = 500,
            commissionPercent = 14,
            commissionFreeShiftActive = true
        )
        assertEquals(0, shiftFree.ownerCommissionSom)
        assertEquals(500, shiftFree.driverNetSom)
        assertEquals(0, shiftFree.commissionPercent)
    }

    @Test
    fun `owner bank config validation masking and public https webhook guard work safely`() {
        assertEquals("•••• 9960", com.example.util.OwnerPayoutGatewayClient.maskAccount("996555889960"))
        assertEquals("Не указаны", com.example.util.OwnerPayoutGatewayClient.maskAccount(""))

        val invalidShortAcc = com.example.data.model.OwnerBankConfig(accountOrCard = "123", beneficiaryName = "ИП Асанов")
        assertNotNull(com.example.util.OwnerPayoutGatewayClient.validateBankConfig(invalidShortAcc))

        val invalidInn = com.example.data.model.OwnerBankConfig(
            accountOrCard = "KG121030010000123456",
            beneficiaryName = "ИП Асанов",
            inn = "12345"
        )
        assertNotNull(com.example.util.OwnerPayoutGatewayClient.validateBankConfig(invalidInn))

        val validConfig = com.example.data.model.OwnerBankConfig(
            bankName = "MBank",
            accountOrCard = "+996 555 889 960",
            beneficiaryName = "ИП Асанов Тимур",
            inn = "20105199000123",
            bik = "103001",
            commissionPercent = 15
        )
        assertEquals(null, com.example.util.OwnerPayoutGatewayClient.validateBankConfig(validConfig))

        assertTrue(com.example.util.OwnerPayoutGatewayClient.isSafePublicHttpsUrl("https://api.kgtaxi.kg/v1/payouts"))
        assertTrue(!com.example.util.OwnerPayoutGatewayClient.isSafePublicHttpsUrl("http://api.kgtaxi.kg/v1/payouts"))
        assertTrue(!com.example.util.OwnerPayoutGatewayClient.isSafePublicHttpsUrl("https://localhost/payout"))
        assertTrue(!com.example.util.OwnerPayoutGatewayClient.isSafePublicHttpsUrl("https://127.0.0.1/payout"))
        assertTrue(!com.example.util.OwnerPayoutGatewayClient.isSafePublicHttpsUrl("https://192.168.1.10/payout"))
        assertTrue(!com.example.util.OwnerPayoutGatewayClient.isSafePublicHttpsUrl("https://10.0.0.5/payout"))
        assertTrue(!com.example.util.OwnerPayoutGatewayClient.isSafePublicHttpsUrl("https://169.254.169.254/latest/meta-data"))
    }
}


