package com.delivery.demo.courier

import com.delivery.demo.order.Order
import java.util.*
import javax.persistence.*
import kotlin.math.sqrt

@Embeddable
data class LatLng(
    @Column(nullable = true)
    val latitude: Float,
    @Column(nullable = true)
    val longitude: Float
) {
    fun distanceTo(location: LatLng): Double {
        // Lol whatever
        val latDiff = location.latitude - latitude
        val lonDiff = location.longitude - longitude
        val distanceSquared = latDiff * latDiff + lonDiff * lonDiff
        return sqrt(distanceSquared.toDouble())
    }
}

@Embeddable
data class LocationReport(
    @Embedded
    val latLng: LatLng,
    val timestamp: Date
)

@Entity
@Table(name = "couriers")
data class Courier(
    @Id
    val id: UUID = UUID.randomUUID(),
    val fullName: String,
    @Embedded
    private var _location: LocationReport? = null,
    private var onShift: Boolean
) {

    val location: LocationReport?
        get() = _location

    fun startShift() {
        onShift = true
    }

    fun stopShift() {
        onShift = false
        // What to do with existing orders?
        // We can assume that couriers off-shift wouldn't get any more orders, but have to complete active ones
    }

    fun assignOrder(order: Order): CourierOrder {
        if (!onShift) {
            throw Exception("Cannot assign orders to couriers off-shift")
        }
        return CourierOrder(order.id, this, order)
    }

    fun updateLocation(location: LocationReport) {
        this._location = location
    }

    override fun toString(): String {
        return "${Courier::class.java.simpleName}$id"
    }

    companion object {
        fun new(fullName: String): Courier = Courier(
            fullName = fullName,
            onShift = false
        )
    }
}

data class CourierDTO(
    val id: String
)

fun Courier.asDTO() = CourierDTO(
    id = id.toString()
)