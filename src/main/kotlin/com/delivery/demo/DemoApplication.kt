package com.delivery.demo

import com.delivery.demo.basket.BasketRepository
import com.delivery.demo.courier.*
import com.delivery.demo.order.*
import com.delivery.demo.restaurant.RestaurantRepository
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.security.SecurityScheme
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import org.joda.money.format.MoneyFormatterBuilder
import org.springdoc.api.OpenApiCustomiser
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.http.MediaType
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler
import org.springframework.stereotype.Component
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque


@Component
class RestaurantsApplication(
    val restaurantRepository: RestaurantRepository,
    val courierRepository: CourierRepository,
    val basketRepository: BasketRepository
) : ApplicationRunner {

    override fun run(args: ApplicationArguments?) {
//        val bg = Restaurant(
//            id = UUID.randomUUID(),
//            name = "Burger King",
//            address = Address(LatLng(0.0f, 0.0f), "address", "city", "country"),
//            currency = CurrencyUnit.EUR
//        )
//        bg.addDish(name = "Whopper", price = Money.of(CurrencyUnit.EUR, 5.50))
//        bg.addDish(name = "Fries", price = Money.of(CurrencyUnit.EUR, 1.50))
//
//        restaurantRepository.save(bg)
//
//        val donerPlace = Restaurant(
//            id = UUID.randomUUID(),
//            name = "Döner place",
//            address = Address(LatLng(10.0f, 10.0f),"Street 2", "City", "Country"),
//            currency = CurrencyUnit.EUR
//        )
//        donerPlace.addDish(name = "Döner", price = Money.of(CurrencyUnit.EUR, 4.50))
//        donerPlace.addDish(name = "Kebap", price = Money.of(CurrencyUnit.EUR, 3.50))
//        restaurantRepository.save(donerPlace)
//
//        val jake = Courier(
//            fullName = "Jake Jakeson",
//            location = LocationReport(LatLng(0.0f, 0.0f), Date()),
//            onShift = true
//        )
//        courierRepository.save(jake)
//
//        val basket = donerPlace.newBasket("Jake")
//        val savedBasket = basketRepository.save(basket)
//        savedBasket.addItem(donerPlace.dishes[0], 1)
    }

}

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}

@SpringBootApplication(scanBasePackages = ["com.delivery"])
@EnableJpaRepositories(basePackages = ["com.delivery"])
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EntityScan(basePackages = ["com.delivery"])
@EnableScheduling
class DemoApplication {

    @Bean("auditorAware")
    fun auditorAware(): AuditorAware<Unit> = AuditorAware {
        Optional.empty<Unit>()
    }

    @Bean
    fun taskScheduler(): TaskScheduler {
        return ConcurrentTaskScheduler()
    }

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .components(
                Components().addSecuritySchemes(
                    "JWT",
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                )
            )
    }

    @Bean
    @Qualifier("publishableEvents")
    fun publishableEvents(): List<Class<out DomainEvent>> = listOf(
        OrderPreparationStarted::class.java,
        OrderPreparationFinished::class.java,
        OrderPlaced::class.java,
        OrderAssigned::class.java,
        OrderPickedUp::class.java,
        OrderDelivered::class.java,
        CourierLocationUpdated::class.java,
        CourierShiftStarted::class.java,
        CourierShiftStopped::class.java,
        CourierAdded::class.java
    )

    @Bean
    fun customerGlobalHeaderOpenApiCustomiser(@Qualifier("publishableEvents") events: List<Class<out DomainEvent>>): OpenApiCustomiser =
        OpenApiCustomiser {
            // Enrich generated API definition with event schemas

            for (event in events) {
                val schemas = ModelConverters.getInstance().read(event)
                it.components.schemas.putAll(schemas)
            }
        }
//
//    @Bean
//    fun requestDumperFilter(): FilterRegistrationBean<RequestDumperFilter> {
//        val registration = FilterRegistrationBean<RequestDumperFilter>()
//        val requestDumperFilter = RequestDumperFilter()
//        registration.filter = requestDumperFilter
//        registration.addUrlPatterns("/*")
//        return registration
//    }
}
interface DomainEvent

interface EventPublisher {
    fun publish(events: List<DomainEvent>, topic: String = "default")
}

interface EventSubscriber {
    fun <T : DomainEvent> subscribe(eventType: Class<T>, topic: String = "default", handler: (T) -> Unit): Subscription
    fun <T : DomainEvent> subscribeAll(
        eventTypes: List<Class<out T>>,
        topic: String = "default",
        handler: (T) -> Unit
    ): Subscription
}

interface Subscription {
    fun unsubscribe()
}

@Configuration
class AbstractTramEventTestConfiguration {

    private val subscribers = ConcurrentLinkedDeque<Pair<Pair<String, Class<out DomainEvent>>, (DomainEvent) -> Unit>>()
    private val EVENT_TYPE_HEADER = "event_type"

    @Bean
    fun eventPublisher(objectMapper: ObjectMapper): EventPublisher {
        return object : EventPublisher {
            override fun publish(events: List<DomainEvent>, topic: String) {
                events.forEach { event ->
                    val key = topic to event::class.java
                    subscribers.filter { it.first == key }.forEach { it.second(event) }
                }
            }
        }
    }

    @Bean
    fun eventSubscriber(objectMapper: ObjectMapper): EventSubscriber {
        val subscriberId = UUID.randomUUID().toString()
        return object : EventSubscriber {
            override fun <T : DomainEvent> subscribeAll(
                eventTypes: List<Class<out T>>,
                topic: String,
                handler: (T) -> Unit
            ): Subscription {
                val subscriptions = eventTypes.map { subscribe(it, topic, handler) }

                return object : Subscription {
                    override fun unsubscribe() {
                        subscriptions.forEach { it.unsubscribe() }
                    }

                }
            }

            override fun <T : DomainEvent> subscribe(
                eventType: Class<T>,
                topic: String,
                handler: (T) -> Unit
            ): Subscription {
                val key = topic to eventType
                val handler = handler as (DomainEvent) -> Unit
                val item = key to handler
                subscribers.add(item)

                return object : Subscription {
                    override fun unsubscribe() {
                        subscribers.remove(item)
                    }

                }
            }
        }
    }
}

@Configuration
internal class WebMvcConfiguration : WebMvcConfigurer {
    override fun configureContentNegotiation(configurer: ContentNegotiationConfigurer) {
        configurer.defaultContentType(MediaType.APPLICATION_JSON)
    }
}

@Configuration
class JacksonConfiguration {

    @Bean
    fun objectMapper(): ObjectMapper {
        val moneyModule = SimpleModule()
        moneyModule.addSerializer(Money::class.java, MoneySerializer())
        moneyModule.addDeserializer(Money::class.java, MoneyDeserializer())

        moneyModule.addSerializer(CurrencyUnit::class.java, object : JsonSerializer<CurrencyUnit>() {
            override fun serialize(value: CurrencyUnit, gen: JsonGenerator, serializers: SerializerProvider) {
                gen.writeString(value.code)
            }
        })
        moneyModule.addDeserializer(CurrencyUnit::class.java, object : JsonDeserializer<CurrencyUnit>() {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext): CurrencyUnit {
                val code = p.readValueAs(String::class.java)
                return CurrencyUnit.of(code)
            }

        })

        return Jackson2ObjectMapperBuilder.json()
            .modulesToInstall(moneyModule)
            .build()
    }

    data class MoneyView(
        val amount: Double,
        val currencyCode: String,
        val formatted: String
    )


    class MoneySerializer : JsonSerializer<Money>() {

        override fun serialize(value: Money, gen: JsonGenerator, serializers: SerializerProvider) {
            // Need to get user local here?
            val formatter =
                MoneyFormatterBuilder().appendCurrencySymbolLocalized().appendAmountLocalized().toFormatter()
            val view = MoneyView(
                amount = value.amount.toDouble(),
                currencyCode = value.currencyUnit.code,
                formatted = formatter.print(value)
            )
            gen.writeObject(view)
        }
    }

    class MoneyDeserializer : JsonDeserializer<Money>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Money {
            val view = p.readValueAs(MoneyView::class.java)
            val currencyUnit = CurrencyUnit.of(view.currencyCode)
            return Money.of(currencyUnit, view.amount)
        }
    }

}

@Component
class MoneyConverter : ModelConverter {

    override fun resolve(
        type: AnnotatedType,
        context: ModelConverterContext,
        chain: MutableIterator<ModelConverter>
    ): Schema<*>? {
        val typeName = type.type.typeName

        var newType = type
        if (type.isSchemaProperty) {
            Json.mapper().constructType(type.type)?.let {
                val cls = it.rawClass
                if (Money::class.java.isAssignableFrom(cls)) {
                    newType = AnnotatedType(JacksonConfiguration.MoneyView::class.java)
                }
            }
        }

        return if (chain.hasNext()) {
            chain.next().resolve(newType, context, chain)
        } else {
            null
        }
    }

    companion object {
        init {
            ModelConverters.getInstance().addConverter(MoneyConverter())
        }
    }
}