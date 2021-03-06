package com.delivery.demo.basket

import com.delivery.demo.EventPublisher
import com.delivery.demo.order.OrderDTO
import com.delivery.demo.order.OrderRepository
import com.delivery.demo.order.asDTO
import com.delivery.demo.profile.ProfileRepostiory
import com.delivery.demo.restaurant.RestaurantRepository
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.util.*
import javax.validation.Valid

/**
 * TODO: Refactor to use Aggregate instead
 */
@RestController
@CrossOrigin
@RequestMapping(
        "/basket",
        produces = [MediaType.APPLICATION_JSON_VALUE]
)
@Tag(name = "basket", description = "Current basket")
class BasketController(
        val restaurantRepository: RestaurantRepository,
        val basketRepository: BasketRepository,
        val orderRepository: OrderRepository,
        val eventPublisher: EventPublisher,
        val profileRepostiory: ProfileRepostiory
) {

    @GetMapping("")
    @ApiResponses(
            ApiResponse(responseCode = "200"),
            ApiResponse(responseCode = "204", content = [Content()])
    )
    fun basket(principal: Principal): ResponseEntity<BasketDTO> {
        // if we have user profile - it's easy
        // what if user is a guest?

        return basketRepository.findByOwner(principal.name)
                .map { ResponseEntity.ok(it.asDTO()) }
                .orElse(ResponseEntity.noContent().build())
    }

    @Transactional
    @PostMapping("/addItem", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun addItemToBasket(@RequestBody @Valid input: AddItemToBasketInput, principal: Principal): BasketDTO {
        val restaurant = restaurantRepository.findById(input.restaurantId)
                .orElseThrow { Exception("Restaurant with id ${input.restaurantId} not found") }

        val dish = restaurant.dishes.find { it.id == input.dishId } ?: throw Exception("Dish not found")

        if (input.quantity <= 0) {
            throw Exception("Invalid quantity")
        }

        val owner = principal.name
        // find current basket
        // if missing, create one
        // Should it be the controller's job to do it?
        val basket = basketRepository.findByOwner(owner).orElseGet {
            val profile = profileRepostiory.findByUserId(principal.name).orElseThrow { Exception("No user profile") }
            val deliveryAddress = profile.deliveryAddress ?: throw Exception("Delivery address not set")
            val newBasket = restaurant.newBasket(owner, deliveryAddress)
            basketRepository.save(newBasket)
        }

        // If basket is not empty
        // And basket already contains dishes from other restaurant
        // throw an error, unless forceNew is passed
        if (basket.items.isNotEmpty() && basket.restaurant.id != input.restaurantId) {
            if (input.forceNewBasket) {
                basket.removeAllItems()
            } else {
                throw Exception("Basket not empty")
            }
        }

        basket.addItem(dish, input.quantity)
        return basket.asDTO()
    }

    @PostMapping("/removeItem", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Transactional
    fun removeItemFromBasket(@RequestBody @Valid input: RemoveFromBasketInput): BasketDTO {
        val restaurant = restaurantRepository.findById(input.restaurantId)
                .orElseThrow { Exception("Restaurant with id ${input.restaurantId} not found") }

        val dish = restaurant.dishes.find { it.id == input.dishId } ?: throw Exception("Dish not found")

        if (input.quantity <= 0) {
            throw Exception("Invalid quantity")
        }

        val owner = userId()
        val basket = basketRepository.findByOwner(owner).orElseThrow { Exception("No basket avaialble") }

        basket.removeItem(dish, input.quantity)

        return basket.asDTO()
    }

    @PostMapping("/checkout")
    @Transactional
    fun checkout(): OrderDTO {
        val owner = userId()
        val basket = basketRepository.findByOwner(owner).orElseThrow { Exception("No basket avaialble") }

        // TODO: Address should be set at the very beginning before searching
        val restaurant = basket.restaurant

        // Create order
        val order = restaurant.placeOrder(
                userId = basket.owner,
                deliveryAddress = basket.deliveryAddress,
                items = basket.items
        )
        orderRepository.save(order)
        eventPublisher.publish(order.events)

        // Clear the basket
        basketRepository.delete(basket)

        return order.asDTO()
    }

    fun userId(): String {
        return SecurityContextHolder.getContext().authentication.principal as String
//        return "Jake"
    }
}

data class AddItemToBasketInput(
        val dishId: UUID,
        val restaurantId: UUID,
        val quantity: Int,
        val forceNewBasket: Boolean = false
)

data class RemoveFromBasketInput(
        val dishId: UUID,
        val restaurantId: UUID,
        val quantity: Int
)
