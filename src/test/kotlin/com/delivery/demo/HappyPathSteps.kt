package com.delivery.demo

import com.delivery.demo.basket.AddItemToBasketInput
import com.delivery.demo.basket.BasketDTO
import com.delivery.demo.courier.*
import com.delivery.demo.order.OrderDTO
import com.delivery.demo.restaurant.*
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java8.En
import io.cucumber.spring.CucumberTestContext
import org.assertj.core.api.Assertions.assertThat
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootContextLoader
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.context.annotation.Scope
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.stereotype.Component
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
import org.springframework.web.client.getForObject
import org.springframework.web.client.postForObject
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.net.URI
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = [DemoApplication::class], loader = SpringBootContextLoader::class)
class HappyPathSteps : En {

    private val restTemplate = RestTemplate()

    @Autowired
    lateinit var world: HappyPathWorld

    @Autowired
    lateinit var restaurantRepository: RestaurantRepository
    @Autowired
    lateinit var courierRepository: CourierRepository
    @Autowired
    lateinit var objectMapper: ObjectMapper

    lateinit var token: String
    lateinit var session: WebSocketSession

    @LocalServerPort
    var serverPort = 0

    private val locationMap = mapOf(
        "PointA" to LatLng(0.0f, 0.0f),
        "PointB" to LatLng(5.0f, 5.0f)
    )

    init {
        Before { _ ->
            val headers = HttpHeaders()
            headers.set("content-type", "application/json")
            val entity = HttpEntity(
                "{\"client_id\":\"<redacted>\",\"client_secret\":\"<redacted>\",\"audience\":\"https://delivery/api\",\"grant_type\":\"client_credentials\"}",
                headers
            )
            val response = restTemplate.exchange<Map<String, String>>(
                "https://dev-delivery.auth0.com/oauth/token",
                HttpMethod.POST,
                entity
            )
            token = response.body!!.getValue("access_token")

            restTemplate.interceptors = listOf(
                ClientHttpRequestInterceptor { request, body, execution ->
                    request.headers.add("Authorization", "Bearer $token")
                    return@ClientHttpRequestInterceptor execution.execute(request, body)
                }
            )

            val client = StandardWebSocketClient()
            session = client.doHandshake(object : TextWebSocketHandler() {
                override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                    val socketMessage = objectMapper.readValue(message.payload, WebSocketMessage::class.java)
                    val event = objectMapper.readValue(socketMessage.payload, Class.forName(socketMessage.type))
                    world.events.add(event)
                }
            }, WebSocketHttpHeaders(), URI.create("ws://127.0.0.1:$serverPort/")).get()

            session.sendMessage(TextMessage("Hey"))
        }
        Given("^restaurant \"(.+)\" located near \"(.+)\" with following dishes$") { restaurantName: String, locationName: String, dataTable: DataTable ->
            val restaurant = Restaurant(
                id = UUID.randomUUID(),
                name = restaurantName,
                address = Address(locationMap.getValue(locationName), "Fake", "Fake", "Fake"),
                currency = CurrencyUnit.USD
            )
            dataTable.asMaps()
                .forEach {
                    val dishName = it.getValue("dish")
                    val price = it.getValue("price").toDouble()
                    restaurant.addDish(dishName, Money.of(restaurant.currency, price))
                }
            restaurantRepository.save(restaurant)
        }

        Given("A signed-in user") {
            // Do we like need two restTemplates with different headers?
            // Aka, on service one, one normal one?
        }
        Given("user's basket is empty") {
            val response = restTemplate.getForObject<BasketDTO?>("http://localhost:$serverPort/api/basket")
            assert(response == null)
        }
        When("user browses list of restaurants") {
            world.restaurants =
                restTemplate.getForObject<Array<RestaurantDTO>>("http://localhost:$serverPort/api/restaurants")
        }
        When("^user browses dishes of \"(.*)\" restaurant$") { restaurantName: String ->
            world.selectedRestaurant = world.restaurants.first { it.name == restaurantName }
            world.dishes =
                restTemplate.getForObject<Array<DishDTO>>("http://localhost:$serverPort/api/restaurants/${world.selectedRestaurant.id}/dishes")
        }
        When("^user adds (.*) \"(.*)\" to basket$") { quantity: Int, dishName: String ->
            val dish = world.dishes.first { it.name == dishName }
            val input = AddItemToBasketInput(
                dishId = UUID.fromString(dish.id),
                restaurantId = UUID.fromString(world.selectedRestaurant.id),
                quantity = quantity
            )
            world.basket =
                restTemplate.postForObject<BasketDTO>("http://localhost:$serverPort/api/basket/addItem", input)
        }
        Then("user's basket should not be empty") {
            val response = restTemplate.getForObject<BasketDTO>("http://localhost:$serverPort/api/basket")
            assertThat(response.items).isNotEmpty
        }
        Then("^user's basket total amount should be (.+)") { totalAmount: Double ->
            // Double comparison, whatever ¯\_(ツ)_/¯
            assertThat(world.basket.totalAmount.amount).isEqualTo(totalAmount)
        }
        When("user performs checkout") {
            world.order = restTemplate.postForObject<OrderDTO>("http://localhost:$serverPort/api/basket/checkout")
        }
        Given("^a courier \"(.+)\"") { courierName: String ->
            val courier = Courier.new(courierName)
            courierRepository.save(courier)
            world.couriers[courierName] = courier.id
        }
        Given("^\"(.+)\" is on shift") { courierName: String ->
            val courierId = world.couriers.getValue(courierName)
            restTemplate.postForObject<CourierDTO>(api("/couriers/$courierId/startShift"))
        }
        Given("^\"(.+)\" updated his location to be near \"(.+)\"") { courierName: String, locationName: String ->
            val courierId = world.couriers.getValue(courierName)
            val input = UpdateLocationInput(locationMap.getValue(locationName))
            restTemplate.postForObject<CourierDTO>(api("/couriers/$courierId/location"), input)
        }
        Then("^\"(.+)\" is assigned to deliver this order") { courierName: String ->
            val courierId = world.couriers.getValue(courierName)
            val orders = restTemplate.getForObject<Array<CourierOrderDTO>>(api("/couriers/$courierId/orders"))
            assertThat(orders.map { it.orderId }).contains(world.order.id)
        }
        Then("^\"(.+)\" receives this order") { restaurantName: String ->
            val restaurantId = world.restaurants.first { it.name == restaurantName }.id
            val orders = restTemplate.getForObject<Array<RestaurantOrderDTO>>(api("/restaurants/$restaurantId/orders"))
            assertThat(orders.map { it.order.id }).contains(world.order.id)
        }
        When("^\"(.+)\" starts to prepare this order") { restaurantName: String ->
            val restaurantId = world.restaurants.first { it.name == restaurantName }.id
            val orderId = world.order.id
            val order =
                restTemplate.postForObject<RestaurantOrderDTO>(api("/restaurants/$restaurantId/orders/$orderId/startPreparing"))
            assertThat(order.status).isEqualTo(RestaurantOrderStatus.Active)
        }
        When("^\"(.+)\" finishes preparing this order") { restaurantName: String ->
            val restaurantId = world.restaurants.first { it.name == restaurantName }.id
            val orderId = world.order.id
            val order =
                restTemplate.postForObject<RestaurantOrderDTO>(api("/restaurants/$restaurantId/orders/$orderId/finishPreparing"))
            assertThat(order.status).isEqualTo(RestaurantOrderStatus.Completed)
        }
        Then("^\"(.+)\" is notified that order preparation started") { courierName: String ->
            retry {
                val events = world.events.filterIsInstance<OrderPreparationStarted>().first()
                assertThat(events.orderId.toString()).isEqualTo(world.order.id)
            }
        }
        Then("^\"(.+)\" is notified that order preparation finished") { courierName: String ->
            retry {
                val events = world.events.filterIsInstance<OrderPreparationFinished>().first()
                assertThat(events.orderId.toString()).isEqualTo(world.order.id)
            }
        }
        When("^\"(.+)\" confirm order pickup") { courierName: String ->
            val courierId = world.couriers.getValue(courierName)
            val orderId = world.order.id
            val order =
                restTemplate.postForObject<CourierOrderDTO>(api("/couriers/$courierId/orders/$orderId/confirmPickup"))
        }
        When("^\"(.+)\" confirm order dropoff") { courierName: String ->
            val courierId = world.couriers.getValue(courierName)
            val orderId = world.order.id
            val order =
                restTemplate.postForObject<CourierOrderDTO>(api("/couriers/$courierId/orders/$orderId/confirmDropoff"))
        }
    }

    fun api(path: String) = "http://localhost:$serverPort/api$path"
}

fun retry(handler: () -> Unit) {
    // TODO: timeout?
    while (true) {
        try {
            handler()
            break
        } catch (e: Exception) {

        }
    }
}

@Component
@Scope(CucumberTestContext.SCOPE_CUCUMBER_GLUE)
class HappyPathWorld {
    lateinit var restaurants: Array<RestaurantDTO>
    lateinit var selectedRestaurant: RestaurantDTO
    lateinit var dishes: Array<DishDTO>
    lateinit var order: OrderDTO
    lateinit var basket: BasketDTO

    val events = ConcurrentLinkedDeque<Any>()

    val couriers = mutableMapOf<String, UUID>()
}