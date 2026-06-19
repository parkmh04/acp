package com.acp.merchant.application.service

import com.acp.merchant.application.port.input.CheckoutUseCase
import com.acp.merchant.application.port.output.*
import com.acp.merchant.domain.model.*
import com.acp.merchant.domain.service.AddressValidator
import com.acp.merchant.domain.service.PricingEngine
import com.acp.merchant.domain.service.ShippingCalculator
import com.acp.schema.checkout.CreateCheckoutSessionRequest
import com.acp.schema.checkout.UpdateCheckoutSessionRequest
import com.acp.schema.payment.PaymentApproveRequest
import com.acp.schema.payment.PaymentItem as PaymentRequestItem
import com.acp.schema.payment.PaymentPrepareRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class CheckoutService(
        private val checkoutRepository: CheckoutRepositoryPort,
        private val productRepository: ProductPersistencePort,
        private val orderRepository: OrderRepositoryPort,
        private val paymentClient: PaymentClient,
        private val pricingEngine: PricingEngine,
        private val shippingCalculator: ShippingCalculator,
        private val addressValidator: AddressValidator,
        private val redissonClient: RedissonClient,
        private val transactionPort: TransactionPort
) : CheckoutUseCase {

    private companion object {
        const val MAX_QUANTITY = 99
        const val SESSION_TTL_MINUTES = 30L
        val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }

    override suspend fun createSession(request: CreateCheckoutSessionRequest): CheckoutSession {
        // 0. 입력 검증
        require(request.items.isNotEmpty()) { "items must not be empty" }
        request.buyer?.email?.let { validateEmail(it) }

        // 1. Fetch products and validate
        val checkoutItems =
                request.items.map { requestItem ->
                    validateQuantity(requestItem.quantity)
                    val product =
                            productRepository.findById(requestItem.id)
                                    ?: throw IllegalArgumentException(
                                            "Product not found: ${requestItem.id}"
                                    )

                    val unitPrice =
                            product.priceAmount
                                    ?: throw IllegalStateException("Product price missing")
                    val totalPrice = unitPrice.multiply(java.math.BigDecimal(requestItem.quantity))

                    CheckoutItem(
                            productId = requestItem.id,
                            quantity = requestItem.quantity,
                            unitPrice = unitPrice,
                            totalPrice = totalPrice
                    )
                }

        // 2. Calculate Totals
        val totals = pricingEngine.calculateTotals(checkoutItems)

        // 3. Determine Available Fulfillment Options
        val address = request.fulfillmentAddress?.let { Address(it.countryCode, it.postalCode) }

        if (address != null) {
            val validationResult = addressValidator.validate(address)
            if (!validationResult.isValid) {
                throw IllegalArgumentException(validationResult.errorMessage)
            }
        }

        val availableOptions =
                if (address != null) {
                    shippingCalculator.getAvailableFulfillmentOptions(
                            checkoutItems,
                            address,
                            totals.itemsBaseAmount
                    )
                } else {
                    emptyList()
                }

        // 4. Create Session
        val session =
                CheckoutSession(
                        id = UUID.randomUUID().toString(),
                        status = CheckoutStatus.NOT_READY,
                        currency = "KRW",
                        items = checkoutItems,
                        buyer = request.buyer?.let { Buyer(it.email, it.name) },
                        shippingAddress = address,
                        availableFulfillmentOptions = availableOptions,
                        totals = totals,
                        expiresAt = ZonedDateTime.now().plusMinutes(SESSION_TTL_MINUTES)
                )

        return checkoutRepository.save(session)
    }

    private fun validateQuantity(quantity: Int) {
        require(quantity in 1..MAX_QUANTITY) { "quantity must be between 1 and $MAX_QUANTITY" }
    }

    private fun validateEmail(email: String) {
        require(EMAIL_REGEX.matches(email)) { "invalid email format: $email" }
    }

    override suspend fun getSession(id: String): CheckoutSession? {
        val session = checkoutRepository.findById(id) ?: return null

        return if (session.shippingAddress != null) {
            session.copy(
                    availableFulfillmentOptions =
                            shippingCalculator.getAvailableFulfillmentOptions(
                                    session.items,
                                    session.shippingAddress,
                                    session.totals.itemsBaseAmount
                            )
            )
        } else {
            session
        }
    }

    override suspend fun updateSession(
            id: String,
            request: UpdateCheckoutSessionRequest
    ): CheckoutSession {
        val existingSession =
                getSession(id) ?: throw NoSuchElementException("Session not found: $id")

        if (existingSession.status != CheckoutStatus.NOT_READY &&
                        existingSession.status != CheckoutStatus.READY
        ) {
            throw IllegalStateException(
                    "Cannot update session in status: ${existingSession.status}"
            )
        }

        // 1. Update Items
        val updatedItems =
                if (request.items != null) {
                    request.items!!.map { requestItem ->
                        validateQuantity(requestItem.quantity)
                        val product =
                                productRepository.findById(requestItem.id)
                                        ?: throw IllegalArgumentException(
                                                "Product not found: ${requestItem.id}"
                                        )

                        val unitPrice =
                                product.priceAmount
                                        ?: throw IllegalStateException("Product price missing")
                        val totalPrice =
                                unitPrice.multiply(java.math.BigDecimal(requestItem.quantity))

                        CheckoutItem(
                                productId = requestItem.id,
                                quantity = requestItem.quantity,
                                unitPrice = unitPrice,
                                totalPrice = totalPrice
                        )
                    }
                } else {
                    existingSession.items
                }

        // 2. Update Buyer and Address
        val updatedBuyer = request.buyer?.let { Buyer(it.email, it.name) } ?: existingSession.buyer
        val updatedAddress =
                request.fulfillmentAddress?.let { Address(it.countryCode, it.postalCode) }
                        ?: existingSession.shippingAddress

        if (updatedAddress != null && updatedAddress != existingSession.shippingAddress) {
            val validationResult = addressValidator.validate(updatedAddress)
            if (!validationResult.isValid) {
                throw IllegalArgumentException(validationResult.errorMessage)
            }
        }

        // 3. Recalculate Available Options
        val itemsChanged = updatedItems != existingSession.items
        val addressChanged = updatedAddress != existingSession.shippingAddress

        var availableOptions = existingSession.availableFulfillmentOptions
        var selectedOptionId = existingSession.selectedFulfillmentOption

        if (itemsChanged || addressChanged) {
            val tempTotals = pricingEngine.calculateTotals(updatedItems)
            availableOptions =
                    if (updatedAddress != null) {
                        shippingCalculator.getAvailableFulfillmentOptions(
                                updatedItems,
                                updatedAddress,
                                tempTotals.itemsBaseAmount
                        )
                    } else {
                        emptyList()
                    }
            if (addressChanged) {
                selectedOptionId = null
            }
        }

        // 4. Handle Option Selection
        if (request.fulfillmentOptionId != null) {
            val isAvailable = availableOptions.any { it.id == request.fulfillmentOptionId }
            if (!isAvailable) {
                throw IllegalArgumentException(
                        "Fulfillment option not available: ${request.fulfillmentOptionId}"
                )
            }
            selectedOptionId = request.fulfillmentOptionId
        }

        // 5. Calculate Shipping Cost
        var shippingCost = java.math.BigDecimal.ZERO
        if (selectedOptionId != null && updatedAddress != null) {
            shippingCost =
                    shippingCalculator.calculateShippingCost(
                            selectedOptionId,
                            pricingEngine.calculateTotals(updatedItems).itemsBaseAmount,
                            updatedAddress
                    )
        }

        // 6. Recalculate Totals
        val newTotals = pricingEngine.calculateTotals(updatedItems, shippingCost)

        // 7. Save Updated Session
        val updatedSession =
                existingSession.copy(
                        items = updatedItems,
                        buyer = updatedBuyer,
                        shippingAddress = updatedAddress,
                        availableFulfillmentOptions = availableOptions,
                        selectedFulfillmentOption = selectedOptionId,
                        totals = newTotals,
                        status =
                                if (updatedBuyer != null &&
                                                updatedAddress != null &&
                                                updatedItems.isNotEmpty() &&
                                                selectedOptionId != null
                                )
                                        CheckoutStatus.READY
                                else CheckoutStatus.NOT_READY,
                        updatedAt = ZonedDateTime.now()
                )

        return checkoutRepository.save(updatedSession)
    }

    override suspend fun completeSession(id: String): CheckoutSession {
        val session =
                checkoutRepository.findById(id)
                        ?: throw NoSuchElementException("Session not found: $id")

        if (session.status == CheckoutStatus.COMPLETED) {
            throw IllegalStateException("Session already completed")
        }

        if (!session.isReadyForPayment()) {
            throw IllegalStateException(
                    "Session not ready for payment. Missing buyer or shipping info."
            )
        }

        val paymentItems =
                session.items.map { item ->
                    val product = productRepository.findById(item.productId)
                    val name = product?.title ?: "Product ${item.productId}"

                    PaymentRequestItem(
                            name = name,
                            quantity = item.quantity,
                            unitPrice = item.unitPrice.toLong(),
                            currency = session.currency
                    )
                }

        val request =
                PaymentPrepareRequest(
                        merchantOrderId = session.id,
                        amount = session.totals.total.toLong(),
                        currency = session.currency,
                        items = paymentItems
                )

        val response = paymentClient.preparePayment(request)

        val updatedSession =
                session.copy(
                        status = CheckoutStatus.READY,
                        nextActionUrl = response.redirectUrl,
                        updatedAt = ZonedDateTime.now()
                )

        return checkoutRepository.save(updatedSession)
    }

    override suspend fun confirmPayment(sessionId: String, pgToken: String): CheckoutSession {
        val lock = redissonClient.getLock("lock:checkout:$sessionId")

        // 락 획득 시도 (최대 5초 대기, 10초간 점유)
        val acquired =
                try {
                    lock.tryLock(5, 10, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    throw IllegalStateException("Locking error: ${e.message}")
                }

        if (!acquired) {
            throw IllegalStateException("Failed to acquire lock for session: $sessionId")
        }

        try {
            // Transaction Boundary Start
            return transactionPort.runInTransaction {
                val session =
                        checkoutRepository.findById(sessionId)
                                ?: throw NoSuchElementException("Session not found: $sessionId")

                if (session.status == CheckoutStatus.COMPLETED) {
                    return@runInTransaction session
                }

                if (session.status != CheckoutStatus.READY) {
                    throw IllegalStateException("Session is not ready for payment completion")
                }

                // 재고 차감 (결제 승인 전, 동일 트랜잭션 내). 부족 시 throw → 전체 롤백.
                session.items.forEach { item ->
                    val deducted = productRepository.decreaseStock(item.productId, item.quantity)
                    if (!deducted) {
                        throw IllegalStateException("재고 부족: ${item.productId}")
                    }
                }

                val approveResponse =
                        paymentClient.approvePayment(
                                PaymentApproveRequest(
                                        merchantOrderId = session.id,
                                        pgToken = pgToken
                                )
                        )

                if (approveResponse.status != "COMPLETED") {
                    throw IllegalStateException(
                            "Payment approval failed: ${approveResponse.status}"
                    )
                }

                val order =
                        Order(
                                id = session.id, // Use session ID as Order ID
                                userId = session.buyer?.email ?: "guest",
                                status = OrderStatus.COMPLETED,
                                totalAmount = session.totals.total,
                                currency = session.currency,
                                paymentRequestIds = approveResponse.paymentId,
                                items =
                                        session.items.map {
                                            val product = productRepository.findById(it.productId)
                                            val productName =
                                                    product?.title ?: "Product ${it.productId}"

                                            OrderLineItem(
                                                    productId = it.productId,
                                                    productName = productName,
                                                    quantity = it.quantity,
                                                    unitPrice = it.unitPrice,
                                                    totalPrice = it.totalPrice
                                            )
                                        }
                        )
                orderRepository.save(order)

                val completedSession =
                        session.copy(
                                status = CheckoutStatus.COMPLETED,
                                updatedAt = ZonedDateTime.now()
                        )

                checkoutRepository.save(completedSession)
            }
            // Transaction Boundary End
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }

    override suspend fun cancelSession(id: String, reason: String): CheckoutSession {
        val session =
                checkoutRepository.findById(id)
                        ?: throw NoSuchElementException("Session not found: $id")

        if (session.status == CheckoutStatus.CANCELED) {
            return session
        }

        if (session.status == CheckoutStatus.COMPLETED) {
            // 결제 완료된 세션은 PSP 취소 후 주문 취소 처리
            val cancelRequest =
                    com.acp.schema.payment.PaymentCancelRequest(
                            merchantOrderId = session.id,
                            reason = reason,
                            amount = session.totals.total.toLong()
                    )

            // PSP 취소 호출
            paymentClient.cancelPayment(cancelRequest)

            // 주문 상태 업데이트
            val order = orderRepository.findById(session.id)
            if (order != null) {
                val canceledOrder = order.copy(status = OrderStatus.CANCELED)
                orderRepository.save(canceledOrder)
            }
        }

        val canceledSession =
                session.copy(status = CheckoutStatus.CANCELED, updatedAt = ZonedDateTime.now())

        return checkoutRepository.save(canceledSession)
    }
}
