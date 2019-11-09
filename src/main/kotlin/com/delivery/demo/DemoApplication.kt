package com.delivery.demo

import com.delivery.demo.restaurant.RestaurantRepository
import com.delivery.demo.restaurant.model.Address
import com.delivery.demo.restaurant.model.Dish
import com.delivery.demo.restaurant.model.Restaurant
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.service.ApiKey
import springfox.documentation.service.AuthorizationScope
import springfox.documentation.service.SecurityReference
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spi.service.contexts.SecurityContext
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.swagger2.annotations.EnableSwagger2


@SpringBootApplication
class DemoApplication(
    val restaurantRepository: RestaurantRepository
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        val bg = Restaurant(
            name = "Burger King",
            address = Address("address", "city", "country")
        )
        bg.dishes.add(Dish(name = "Whopper", restaurant = bg))
        bg.dishes.add(Dish(name = "Fries", restaurant = bg))

        restaurantRepository.save(bg)

        val donerPlace = Restaurant(
            name = "Döner place",
            address = Address("Street 2", "City", "Country")
        )
        donerPlace.dishes.add(Dish(name = "Döner", restaurant = donerPlace))
        donerPlace.dishes.add(Dish(name = "Kebap", restaurant = donerPlace))
        restaurantRepository.save(donerPlace)
    }

}

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}

@Configuration
@EnableSwagger2
class SwaggerConfig {
    @Bean
    fun api(): Docket {
        return Docket(DocumentationType.SWAGGER_2)
            .select()
            .apis(RequestHandlerSelectors.basePackage("com.delivery.demo"))
            .paths(PathSelectors.any())
            .build()
            .produces(setOf("application/json"))
            .consumes(setOf("application/json"))
            .securityContexts(listOf(securityContext()))
            .securitySchemes(listOf(apiKey()))
    }

    private fun apiKey(): ApiKey {
        return ApiKey("JWT", "Authorization", "header")
    }

    private fun securityContext(): SecurityContext {
        return SecurityContext.builder()
            .securityReferences(defaultAuth())
            .forPaths(PathSelectors.regex("/api/.*"))
            .build()
    }

    fun defaultAuth(): List<SecurityReference> {
        val authorizationScope = AuthorizationScope("global", "accessEverything")
        val authorizationScopes = arrayOfNulls<AuthorizationScope>(1)
        authorizationScopes[0] = authorizationScope
        return listOf(
            SecurityReference("JWT", authorizationScopes)
        )
    }
}

@Configuration
internal class WebMvcConfiguration : WebMvcConfigurer {
    override fun configureContentNegotiation(configurer: ContentNegotiationConfigurer) {
        configurer.defaultContentType(MediaType.APPLICATION_JSON)
    }
}