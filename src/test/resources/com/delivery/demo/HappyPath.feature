Feature: HappyPath

  Background:
    Given restaurant "Joes" located near "PointA" with following dishes
      | dish   | price |
      | Burger | 6.00  |
      | Fries  | 3.50  |
    Given a courier "Jake"
    And "Jake" is on shift
    And "Jake" updated his location to be near "PointB"
    And a courier "Mike"
    And "Mike" is on shift
    And "Mike" updated his location to be near "PointA"

  Scenario: User orders a delivery
    Given A signed-in user
    And user sets their address to be near "PointA"
    And user sets valid payment method
    And user's basket is empty

    When user browses list of restaurants
    And user browses dishes of "Joes" restaurant
    And user adds 2 "Burger" to basket
    And user adds 1 "Fries" to basket
    Then user's basket should not be empty
    And user's basket total amount should be 15.50

    When user performs checkout
    Then "Mike" receives a delivery request
    When "Mike" accepts this delivery request
    Then "Mike" is assigned to deliver this order
    And "Joes" receives this order

    When "Joes" starts to prepare this order
    Then "Mike" is notified that order preparation started
    And user can see their order as "being prepared"

    When "Joes" finishes preparing this order
    Then "Mike" is notified that order preparation finished
    And user can see their order as "being picked up"

    When "Mike" confirm order pickup
    And user can see their order as "in delivery"

    When "Mike" confirm order dropoff
    And user can see their order as "delivered"
