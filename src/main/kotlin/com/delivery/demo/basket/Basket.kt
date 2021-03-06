package com.delivery.demo.basket

import com.delivery.demo.AbstractEntity
import com.delivery.demo.Address
import com.delivery.demo.restaurant.Dish
import com.delivery.demo.restaurant.Restaurant
import org.joda.money.Money
import java.math.BigDecimal
import javax.persistence.*

@Entity
@Table(name = "baskets")
class Basket(
        val owner: String,
        val deliveryAddress: Address,
        restaurant: Restaurant
) : AbstractEntity() {

    @OneToMany(mappedBy = "basket", cascade = [CascadeType.ALL])
    var items: MutableList<BasketItem> = mutableListOf()
        protected set

    @ManyToOne
    @JoinColumn(name = "restaurant_id")
    val restaurant: Restaurant = restaurant

    val totalAmount: Money
        get() = if (items.isEmpty()) {
            Money.of(restaurant.currency, BigDecimal.ZERO)
        } else {
            items.map { it.dish.price.multipliedBy(it.quantity.toLong()) }.reduce { acc, money ->
                acc + money
            }
        }

    val isAboveMinimumOrder: Boolean
        get() {
            val minimumAmount = restaurant.minimumOrderAmount ?: Money.zero(restaurant.currency)
            return totalAmount > minimumAmount
        }

    fun addItem(dish: Dish, quantity: Int) {
        val basketItemIndex = items.indexOfFirst { it.dish.id == dish.id }
        if (basketItemIndex != -1) {
            items[basketItemIndex].quantity = items[basketItemIndex].quantity + quantity
        } else {
            items.add(
                    BasketItem(
                            dish = dish,
                            quantity = quantity,
                            basket = this
                    )
            )
        }
    }

    fun removeItem(dish: Dish, quantity: Int) {
        val basketItemIndex = items.indexOfFirst { it.dish.id == dish.id }
        // Don't fail trying to remove non-existent items
        val basketItem = if (basketItemIndex >= 0) items[basketItemIndex] else return
        if (quantity >= basketItem.quantity) {
            items.remove(basketItem)
        } else {
            basketItem.quantity -= quantity
        }
    }

    fun checkout() {
        if (!isAboveMinimumOrder) {
            throw Exception("Cannot checkout: below minimum order")
        }

        // Generate PaymentIntent using `totalAmount`
        // Send it to client?
//        status = WaitingForPayment.

    }

    fun removeAllItems() {
        items.clear()
    }
}

@Entity
@Table(name = "basket_items")
class BasketItem(
        @ManyToOne @JoinColumn(name = "dish_id") val dish: Dish,
        @ManyToOne @JoinColumn(name = "basket_id") val basket: Basket,
        var quantity: Int
) : AbstractEntity() {
    val totalPrice: Money
        get() = dish.price.multipliedBy(quantity.toLong())
}