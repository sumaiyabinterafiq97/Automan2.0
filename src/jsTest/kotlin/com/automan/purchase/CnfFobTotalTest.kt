package com.automan.purchase

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Booking-screen chassis total. Freight input is [globalFreightValues]
 * (set by the freight page). FOB ignores that map. C&F adds it.
 * The car object's freight field is not the source for this function.
 */
class CnfFobTotalTest {

    @Test
    fun fobTotalExcludesFreightAndCnfTotalIncludesIt() {
        val chassis = "NZE141-FOB"
        globalFreightValues.clear()
        globalShippingChargeValues.clear()
        js("window.cnfFormState = {}")
        globalFreightValues[chassis] = 50_000.0

        val car = js("({})")
        car.price = "1000000"
        car.auctionFee = "0"
        car.rixoPrice = "0"
        car.shipmentCharges = "20000"
        car.freight = "999999"
        car.inspectionFee = "0"
        car.repairCharges = "0"
        car.auctionPenaltyFee = "0"
        car.miscCharges = "0"
        car.profit = "0"

        val fob = computeTotalCnfOrFobForChassis(car, chassis, isFobMode = true)
        val cnf = computeTotalCnfOrFobForChassis(car, chassis, isFobMode = false)

        assertEquals(1_020_000.0, fob)
        assertEquals(1_070_000.0, cnf)

        globalFreightValues.clear()
        globalShippingChargeValues.clear()
    }

    @Test
    fun zeroFreightMakesCnfEqualFobAndEachChassisUsesItsOwnFreight() {
        val first = "P0-CNF-A"
        val second = "P0-CNF-B"
        globalFreightValues.clear()
        globalShippingChargeValues.clear()
        js("window.cnfFormState = {}")
        globalFreightValues[first] = 0.0
        globalFreightValues[second] = 10_000.0

        val car = js("({})")
        car.price = "100000"
        car.auctionFee = "20000"
        car.profit = "5000"

        val fob = computeTotalCnfOrFobForChassis(car, first, isFobMode = true)
        val cnf = computeTotalCnfOrFobForChassis(car, first, isFobMode = false)
        val other = computeTotalCnfOrFobForChassis(car, second, isFobMode = false)
        val emptyCar = computeTotalCnfOrFobForChassis(js("({})"), "P0-CNF-EMPTY", isFobMode = false)

        assertEquals(125_000.0, fob)
        assertEquals(125_000.0, cnf)
        assertEquals(135_000.0, other)
        assertEquals(0.0, emptyCar)

        globalFreightValues.clear()
        globalShippingChargeValues.clear()
    }
}
