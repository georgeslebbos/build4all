package com.build4all.order.service.impl;

import com.build4all.cart.domain.Cart;
import com.build4all.cart.domain.CartItem;
import com.build4all.cart.domain.CartStatus;
import com.build4all.cart.repository.CartItemRepository;
import com.build4all.cart.repository.CartRepository;
import com.build4all.catalog.domain.Country;
import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.domain.Item;
import com.build4all.catalog.domain.Region;
import com.build4all.catalog.repository.CountryRepository;
import com.build4all.catalog.repository.CurrencyRepository;
import com.build4all.catalog.repository.ItemRepository;
import com.build4all.catalog.repository.RegionRepository;
import com.build4all.order.domain.Order;
import com.build4all.order.domain.OrderItem;
import com.build4all.order.domain.OrderStatus;
import com.build4all.order.dto.CartLine;
import com.build4all.order.dto.CheckoutRequest;
import com.build4all.order.dto.CheckoutSummaryResponse;
import com.build4all.order.dto.ShippingAddressDTO;
import com.build4all.order.repository.OrderItemRepository;
import com.build4all.order.repository.OrderRepository;
import com.build4all.order.repository.OrderStatusRepository;
import com.build4all.order.service.CheckoutPricingService;
import com.build4all.order.service.OrderService;
import com.build4all.user.domain.Users;
import com.build4all.user.repository.UsersRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import com.build4all.payment.dto.StartPaymentResponse;
import com.build4all.payment.service.PaymentOrchestratorService;
import com.build4all.promo.service.CouponService;
import com.build4all.payment.repository.PaymentMethodRepository;
import com.build4all.payment.domain.PaymentMethod;

import com.build4all.payment.service.OrderPaymentReadService;
import com.build4all.payment.service.OrderPaymentWriteService;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OrderServiceImpl
 *
 * This service is responsible for:
 * - Creating orders (legacy single-item bookings + new generic checkout)
 * - Validating capacity/stock rules
 * - Handling order status transitions (pending/canceled/refunded/completed...)
 * - Integrating checkout pricing (shipping + tax + coupon)
 * - Starting the payment process via PaymentOrchestratorService (Stripe/Cash/...)
 *
 * Notes:
 * - @Transactional ensures that creating order header + order items is atomic.
 * - The new checkout flow is: create order -> start payment -> return payment info to client.
 *
 * Multi-tenant note:
 * - ownerProjectId is the tenant/app scope.
 * - Stripe publishableKey MUST be tenant-scoped (loaded from DB configJson),
 *   NOT compiled into Env or build-time variables.
 */
@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    // ===============================
    // Repositories (Persistence)
    // ===============================

    /** OrderItem data access (lines of an order) */
    private final OrderItemRepository orderItemRepo;

    /** Order header data access (order itself) */
    private final OrderRepository orderRepo;

    /** Users lookup (who is making the order) */
    private final UsersRepository usersRepo;

    /** Item lookup (activities/products that are being ordered) */
    private final ItemRepository itemRepo;

    /** Currency lookup (USD/LBP/SAR...) */
    private final CurrencyRepository currencyRepo;

    /** Status lookup (PENDING/COMPLETED/CANCELED/...) */
    private final OrderStatusRepository orderStatusRepo;

    private final OrderPaymentReadService paymentRead;
    private final OrderPaymentWriteService paymentWrite;

    /** Country lookup (shipping address) */
    private final CountryRepository countryRepo;

    /** Region lookup (shipping address) */
    private final RegionRepository regionRepo;
    
    private final CouponService couponService;

    /** Cart items delete/cleanup after checkout */
    private final CartItemRepository cartItemRepo;
    
    

    // Pricing engine: shipping + taxes + coupons
    /**
     * Central pricing engine that calculates:
     * - items subtotal
     * - shipping cost (based on shipping method + address)
     * - taxes (items + shipping)
     * - coupon discount
     * - grand total
     *
     * Important:
     * - Pricing should be trusted ONLY from server side (never from client inputs).
     * - This protects against price tampering.
     */
    private final CheckoutPricingService checkoutPricingService;

    /**
     * Cart repository:
     * - Used to fetch the ACTIVE cart for the user
     * - Used to mark it as CONVERTED after we successfully start a payment
     */
    private final CartRepository cartRepo;

    // ===============================
    // Payment module integration
    // ===============================

    /**
     * PaymentOrchestratorService:
     * - Provider-agnostic payment engine
     * - Creates internal PaymentTransaction record
     * - Delegates to provider plugin (Stripe/Cash/PayPal...)
     * - Returns bootstrap info for client (clientSecret, publishableKey, redirectUrl...)
     *
     * Stripe-specific:
     * - clientSecret is per-payment-attempt (per order) and is safe to send to client
     * - publishableKey is required to initialize Stripe SDK on client
     * - secretKey must NEVER be sent to client (stays server-side in configJson)
     */
    private final PaymentOrchestratorService paymentOrchestrator;

    /**
     * PaymentMethod repository:
     * - Used to load PaymentMethod entity by code (STRIPE/CASH/...)
     * - We store it on Order header for reporting + later reconciliation
     */
    private final PaymentMethodRepository paymentMethodRepo;

    /**
     * Constructor injection for all dependencies.
     * Spring will auto-wire these based on beans/repositories.
     */
    public OrderServiceImpl(
    	    OrderItemRepository orderItemRepo,
    	    OrderRepository orderRepo,
    	    UsersRepository usersRepo,
    	    ItemRepository itemRepo,
    	    CurrencyRepository currencyRepo,
    	    OrderStatusRepository orderStatusRepo,
    	    CountryRepository countryRepo,
    	    RegionRepository regionRepo,
    	    CheckoutPricingService checkoutPricingService,
    	    CartRepository cartRepo,
    	    CartItemRepository cartItemRepo,
    	    PaymentOrchestratorService paymentOrchestrator,
    	    PaymentMethodRepository paymentMethodRepo,
    	    OrderPaymentReadService paymentRead,
    	    OrderPaymentWriteService paymentWrite,
    	    CouponService couponService // ✅ ADD
    	) {
    	  
    	   this.couponService = couponService; // ✅ ADD
        this.orderItemRepo = orderItemRepo;
        this.orderRepo = orderRepo;
        this.usersRepo = usersRepo;
        this.itemRepo = itemRepo;
        this.currencyRepo = currencyRepo;
        this.orderStatusRepo = orderStatusRepo;
        this.countryRepo = countryRepo;
        this.regionRepo = regionRepo;
        this.checkoutPricingService = checkoutPricingService;
        this.cartRepo = cartRepo;
        this.cartItemRepo = cartItemRepo;
        this.paymentOrchestrator = paymentOrchestrator;
        this.paymentMethodRepo = paymentMethodRepo;
        this.paymentRead = paymentRead;
        this.paymentWrite = paymentWrite;
    }

    /* ===============================
       STATUS HELPERS
       =============================== */

    /**
     * Ensures a status exists in DB (order_status table).
     * Example inputs: "PENDING", "COMPLETED", "CANCELED", "REFUNDED"...
     *
     * Why this helper exists:
     * - Keeps code readable: requireStatus("PENDING") is clearer than repository calls everywhere
     * - Centralizes the exception message when a status is missing in DB
     */
    private OrderStatus requireStatus(String code) {
        return orderStatusRepo.findByNameIgnoreCase(code)
                .orElseThrow(() -> new IllegalStateException("OrderStatus not found: " + code));
    }

    /**
     * Returns the current order status code as uppercase string.
     *
     * Used for business rule checks such as:
     * - if COMPLETED => cannot cancel
     * - if CANCELED => no-op
     *
     * Safe-guards:
     * - Returns empty string if status is missing to avoid NullPointerException.
     */
    private String currentStatusCode(Order header) {
        if (header == null || header.getStatus() == null || header.getStatus().getName() == null)
            return "";
        return header.getStatus().getName().toUpperCase(Locale.ROOT);
    }

    /**
     * Changes status of an order (via OrderItem -> Order header),
     * and updates timestamps. Saves both header and item.
     *
     * Why it takes OrderItem (and not orderId):
     * - Many actions in the system are triggered by orderItemId
     * - OrderItem provides access to the header and avoids re-loading twice
     *
     * Side effects:
     * - Updates Order.orderDate to "now" (you are using it as "last update" timeline)
     * - Updates OrderItem.updatedAt
     */
    private void flipStatus(OrderItem oi, String newStatusUpper) {
        Order header = oi.getOrder();
        if (header == null)
            throw new IllegalStateException("Missing order header");

        OrderStatus statusEntity = requireStatus(newStatusUpper);
        header.setStatus(statusEntity);

        // update dates (useful for timelines/audit)
        header.setOrderDate(LocalDateTime.now());
        oi.setUpdatedAt(LocalDateTime.now());

        orderRepo.save(header);
        orderItemRepo.save(oi);
    }

    /* ===============================
       PRICING HELPERS
       =============================== */

    /**
     * Resolves the unit price used in checkout/order items.
     *
     * Design goal:
     * - All verticals (ecommerce, activities, services...) share the same checkout pipeline
     * - But price fields might differ by type
     *
     * Rules:
     * - If Item is an ecommerce Product -> use effective price (discount applied)
     * - Otherwise -> use Item.price
     * - If missing -> return 0 (avoid NPE and let pricing engine decide final behavior)
     */
    private BigDecimal resolveUnitPrice(Item item) {
        if (item == null) return BigDecimal.ZERO;

        // If ecommerce Product has an effective price (discounted)
        if (item instanceof com.build4all.features.ecommerce.domain.Product product) {
            BigDecimal eff = product.getEffectivePrice();
            return eff != null ? eff : BigDecimal.ZERO;
        }

        BigDecimal base = item.getPrice();
        return base != null ? base : BigDecimal.ZERO;
    }

    /* ===============================
       CAPACITY / OWNERSHIP HELPERS
       =============================== */

    /**
     * Tries to detect the capacity/stock field by reflection,
     * because Build4All supports multiple vertical item types
     * that might name capacity differently:
     *  - getMaxParticipants (activities)
     *  - getStock (ecommerce)
     *  - getSeats, getCapacity, ...
     *
     * Why reflection:
     * - Avoids forcing a single interface across unrelated domains
     * - Keeps checkout generic for different item types
     *
     * Returns:
     * - Integer if found
     * - null if this item doesn't have a capacity/stock concept
     *
     * Performance:
     * - Reflection is used only during checkout (not in listing endpoints),
     *   so overhead is acceptable.
     */
    private Integer tryCapacity(Item item) {
        // activities seats ONLY
        String[] names = {"getMaxParticipants", "getCapacity", "getSeats", "getQuantityLimit"};
        for (String n : names) {
            try {
                Method m = item.getClass().getMethod(n);
                Object v = m.invoke(item);
                if (v instanceof Integer i) return i;
                if (v instanceof Number x) return x.intValue();
            } catch (Exception ignored) {}
        }
        return null;
    }


    /**
     * Ensures the orderItem belongs to the current user.
     *
     * Why:
     * - Prevents user from mutating someone else’s order item
     * - Used in user operations (cancel, request cancel, reset...)
     *
     * IMPORTANT security note:
     * - This must be called with userId derived from JWT/session, NOT user input.
     */
    private OrderItem requireUserOwned(Long orderItemId, Long userId) {
        return orderItemRepo.findByIdAndUser(orderItemId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found or not yours"));
    }

    /**
     * Ensures the orderItem belongs to the current business.
     *
     * Used in business operations:
     * - approve cancel
     * - reject cancel
     * - mark paid/refunded
     *
     * IMPORTANT security note:
     * - businessId must be derived from authenticated business principal.
     */
    private OrderItem requireBusinessOwned(Long orderItemId, Long businessId) {
        return orderItemRepo.findByIdAndBusiness(orderItemId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found or not your business"));
    }

    /* ===============================
       CREATE ORDER (STRIPE) - legacy activities single item
       (kept as-is; you can migrate later to the new checkout+payment flow)
       =============================== */

    /**
     * Legacy booking flow (activities only):
     * - Client pays first and sends stripePaymentId
     * - Server validates PaymentIntent status == succeeded
     * - Then server creates Order + OrderItem
     *
     * Why it's "legacy":
     * - Payment is done BEFORE order creation (client-driven)
     * - New flow is server-driven: create order -> start payment -> return clientSecret
     *
     * Note:
     * - This code uses Stripe secret key from server env (Stripe SDK).
     * - In multi-tenant mode you should eventually validate using tenant config,
     *   but it's OK to keep as-is for now until fully migrated.
     */
    @Override
    public OrderItem createBookItem(Long userId, Long itemId, int quantity,
                                    String stripePaymentId, Long currencyId) {

        // Validate required inputs
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (itemId == null) throw new IllegalArgumentException("itemId is required");
        if (quantity <= 0) throw new IllegalArgumentException("participants must be > 0");
        if (stripePaymentId == null || stripePaymentId.isBlank())
            throw new IllegalArgumentException("stripePaymentId is required");

        // Stripe validation (legacy)
        // Ensures payment is completed before creating the order.
        try {
            var pi = com.stripe.model.PaymentIntent.retrieve(stripePaymentId);
            if (pi == null || !"succeeded".equalsIgnoreCase(pi.getStatus()))
                throw new IllegalStateException("Stripe payment not confirmed");
        } catch (Exception e) {
            throw new IllegalStateException("Stripe error: " + e.getMessage());
        }

        // Load user & item
        Users user = usersRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Item item = itemRepo.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        // Optional currency
        Currency currency = null;
        if (currencyId != null) {
            currency = currencyRepo.findById(currencyId)
                    .orElseThrow(() -> new IllegalArgumentException("Currency not found"));
        }

        // Capacity check (only if item supports it)
        Integer capacity = tryCapacity(item);
        if (capacity != null) {
        	List<String> reservedStatuses = List.of("PENDING", "COMPLETED", "CANCEL_REQUESTED");

        	int already = orderItemRepo.sumQuantityByItemIdAndStatusNames(
        	        item.getId(), reservedStatuses
        	);

        	int remaining = capacity - already;
        
            if (quantity > remaining)
                throw new IllegalStateException("Not enough seats available");
        }

        // Compute totals
        BigDecimal unit = resolveUnitPrice(item);
        BigDecimal total = unit.multiply(BigDecimal.valueOf(quantity));

        // Create order header
        Order order = new Order();
        order.setUser(user);
        order.setStatus(requireStatus("PENDING"));
        order.setOrderDate(LocalDateTime.now());
        order.setTotalPrice(total);
        if (currency != null) order.setCurrency(currency);
        order = orderRepo.save(order);

        // Create line
        OrderItem line = new OrderItem();
        line.setOrder(order);
        line.setItem(item);
        line.setUser(user);
        line.setQuantity(quantity);
        line.setPrice(unit);
        if (currency != null) line.setCurrency(currency);
        line.setCreatedAt(LocalDateTime.now());

        return orderItemRepo.save(line);
    }

    /* ===============================
       CREATE ORDER (CASH) - legacy activities single item
       =============================== */

    /**
     * Legacy cash booking flow:
     * - Business creates a booking manually (cash)
     * - No payment provider validation
     * - Creates PENDING order + item line
     *
     * Why it exists:
     * - Some businesses operate offline or want manual reconciliation
     *
     * Note:
     * - In the NEW generic checkout flow, CASH can be supported by:
     *   paymentOrchestrator.startPayment(...) returning OFFLINE_PENDING
     */
    @Override
    public OrderItem createCashorderByBusiness(Long itemId, Long businessUserId,
                                               int quantity, boolean wasPaid, Long currencyId) {

        // Validate required inputs
        if (itemId == null) throw new IllegalArgumentException("itemId is required");
        if (businessUserId == null) throw new IllegalArgumentException("businessUserId is required");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");

        // Load user & item
        Users user = usersRepo.findById(businessUserId)
                .orElseThrow(() -> new IllegalArgumentException("Business user not found"));
        Item item = itemRepo.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        // Optional currency
        Currency currency = null;
        if (currencyId != null) {
            currency = currencyRepo.findById(currencyId)
                    .orElseThrow(() -> new IllegalArgumentException("Currency not found"));
        }

        // Capacity check (only if item supports it)
        Integer capacity = tryCapacity(item);
        if (capacity != null) {
        	List<String> reservedStatuses = List.of("PENDING", "COMPLETED", "CANCEL_REQUESTED");

        	int already = orderItemRepo.sumQuantityByItemIdAndStatusNames(
        	        item.getId(), reservedStatuses
        	);

            int remaining = capacity - already;
            if (quantity > remaining)
                throw new IllegalStateException("Not enough seats available" + item.getId());
        }

        // Compute totals
        BigDecimal unit = resolveUnitPrice(item);
        BigDecimal total = unit.multiply(BigDecimal.valueOf(quantity));

        // Create order header
        Order order = new Order();
        order.setUser(user);
        order.setStatus(requireStatus("PENDING"));
        order.setOrderDate(LocalDateTime.now());
        order.setTotalPrice(total);
        if (currency != null) order.setCurrency(currency);
        order = orderRepo.save(order);

        // Create line
        OrderItem line = new OrderItem();
        line.setOrder(order);
        line.setItem(item);
        line.setUser(user);
        line.setQuantity(quantity);
        line.setPrice(unit);
        if (currency != null) line.setCurrency(currency);
        line.setCreatedAt(LocalDateTime.now());

        return orderItemRepo.save(line);
    }

    /* ===============================
       QUERIES
       =============================== */

    /**
     * Prevents double-booking (same user booking same item).
     *
     * Current behavior:
     * - Checks existence of any orderItem with same (itemId, userId)
     *
     * Possible future improvement:
     * - Restrict to "COMPLETED" only if you want to allow re-try for failed payments.
     */
    @Override
    public boolean hasUserAlreadyBooked(Long itemId, Long userId) {
        return orderItemRepo.existsByItem_IdAndUser_Id(itemId, userId);
    }

    /**
     * Returns current user's orders (order items) sorted by creation time descending.
     * Useful for "My Orders" screen.
     */
    @Override
    public List<OrderItem> getMyorders(Long userId) {
        return orderItemRepo.findByUser_IdOrderByCreatedAtDesc(userId);
    }

    /**
     * Returns user's orders filtered by a status code (PENDING/COMPLETED/...).
     *
     * Implementation note:
     * - This loads then filters in memory.
     * - You can optimize later with a repository query (status join filtering).
     */
    @Override
    public List<OrderItem> getMyordersByStatus(Long userId, String status) {
        final String wanted = status == null ? "" : status.toUpperCase(Locale.ROOT);

        return orderItemRepo.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .filter(oi -> {
                    Order h = oi.getOrder();
                    if (h == null || h.getStatus() == null || h.getStatus().getName() == null) return false;
                    return h.getStatus().getName().equalsIgnoreCase(wanted);
                })
                .collect(Collectors.toList());
    }

    /* ===============================
       MUTATIONS
       =============================== */

    /**
     * User cancels an order item as long as it is not completed.
     *
     * Rules:
     * - If COMPLETED -> reject (cannot cancel completed order)
     * - If already CANCELED -> no-op
     * - Else -> set status to CANCELED
     *
     * Note:
     * - If you want to handle Stripe refunds automatically, this method should
     *   eventually call payment module to initiate refund (future).
     */
    @Override
    public void cancelorder(Long orderItemId, Long actorId) {
        var oi = requireUserOwned(orderItemId, actorId);
        Order order = oi.getOrder();
        String curr = currentStatusCode(order);

        if ("COMPLETED".equals(curr)) throw new IllegalStateException("Cannot cancel completed");
        if ("CANCELED".equals(curr)) return;

        var ps = paymentRead.summaryForOrder(order.getId(), order.getTotalPrice());
        if (!ps.isFullyPaid()) {
            Order full = orderRepo.findByIdWithItems(order.getId())
                    .orElseThrow(() -> new IllegalStateException("Order not found"));
            releaseStockForOrder(full);

            releaseCouponForOrderIfAny(full);
            String provider = (order.getPaymentMethod() == null || order.getPaymentMethod().getName() == null)
                    ? "CASH"
                    : order.getPaymentMethod().getName();

            paymentWrite.recordPaymentFailedOrCanceled(order.getId(), provider, "USER_CANCELED");

        }

        flipStatus(oi, "CANCELED");
    }


    /**
     * User restores an order back to PENDING (if not completed).
     * Useful if you support "undo cancel" before business processes it.
     */
    @Override
    public void resetToPending(Long orderItemId, Long actorId) {
        var oi = requireUserOwned(orderItemId, actorId);
        var header = oi.getOrder();
        String curr = currentStatusCode(header);

        if ("COMPLETED".equals(curr))
            throw new IllegalStateException("Cannot reset a completed order");

        flipStatus(oi, "PENDING");
    }

    /**
     * Deletes order item.
     *
     * Current behavior:
     * - Tries user-owned deletion first
     * - If not found, falls back to findById (admin-like behavior)
     *
     * ⚠️ Security caution:
     * - This is permissive. In production, restrict fallback path to Admin role only.
     */
    @Override
    public void deleteorder(Long orderItemId, Long actorId) {
        orderItemRepo.findByIdAndUser(orderItemId, actorId)
                .or(() -> orderItemRepo.findById(orderItemId))
                .orElseThrow(() -> new IllegalArgumentException("Order item not found or not yours"));

        orderItemRepo.deleteById(orderItemId);
    }

    /**
     * Refund rules:
     * - If already CANCELED -> mark REFUNDED
     * - If COMPLETED -> not refundable (your current rule)
     * - If PENDING or CANCEL_REQUESTED -> cancel then refund
     *
     * Note:
     * - This is status-only logic (no provider refund).
     * - For Stripe refunds: integrate with Payment module later (recommended).
     */
    @Override
    public void refundIfEligible(Long orderItemId, Long actorId) {
        var oi = orderItemRepo.findById(orderItemId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found"));

        var header = oi.getOrder();
        String curr = currentStatusCode(header);

        switch (curr) {
            case "CANCELED" -> flipStatus(oi, "REFUNDED");
            case "COMPLETED" -> throw new IllegalStateException("Completed orders cannot be refunded");
            case "PENDING", "CANCEL_REQUESTED" -> {
                flipStatus(oi, "CANCELED");
                flipStatus(oi, "REFUNDED");
            }
            default -> throw new IllegalStateException("Unknown status: " + curr);
        }
    }

    /**
     * User requests cancellation (soft cancel) so business can approve/reject.
     *
     * Rules:
     * - If COMPLETED -> not allowed
     * - If already CANCELED -> no-op
     * - Else -> status becomes CANCEL_REQUESTED
     */
    @Override
    public void requestCancel(Long orderItemId, Long userId) {
        var oi = requireUserOwned(orderItemId, userId);
        String curr = currentStatusCode(oi.getOrder());

        if ("COMPLETED".equals(curr))
            throw new IllegalStateException("Cannot request cancel on a completed order");

        if ("CANCELED".equals(curr)) return;

        flipStatus(oi, "CANCEL_REQUESTED");
    }

    /**
     * Business approves cancel request and sets order to CANCELED.
     */
    @Override
    public void approveCancel(Long orderItemId, Long businessId) {
        var oi = requireBusinessOwned(orderItemId, businessId);
        Order order = oi.getOrder();
        String curr = currentStatusCode(order);

        if ("COMPLETED".equals(curr)) throw new IllegalStateException("Cannot approve cancel for completed");
        if ("CANCELED".equals(curr)) return;

        var ps = paymentRead.summaryForOrder(order.getId(), order.getTotalPrice());
        if (!ps.isFullyPaid()) {
            Order full = orderRepo.findByIdWithItems(order.getId()).orElseThrow();
            releaseStockForOrder(full);
            paymentWrite.recordPaymentFailedOrCanceled(order.getId(), providerForOrder(order), "CANCEL_APPROVED");

        }

        flipStatus(oi, "CANCELED");
    }

    /**
     * Business rejects cancel request and restores order to PENDING.
     */
    @Override
    public void rejectCancel(Long orderItemId, Long businessId) {
        var oi = requireBusinessOwned(orderItemId, businessId);
        String curr = currentStatusCode(oi.getOrder());

        if ("CANCELED".equals(curr))
            throw new IllegalStateException("Already canceled");

        if ("COMPLETED".equals(curr)) return;

        flipStatus(oi, "PENDING");
    }

    /**
     * Business marks a canceled order as refunded (status-only).
     */
    @Override
    public void markRefunded(Long orderItemId, Long businessId) {
        var oi = requireBusinessOwned(orderItemId, businessId);
        flipStatus(oi, "REFUNDED");
    }

    /**
     * Returns business orders.
     * Repository method likely performs joins for "rich" results.
     */
    @Override
    public List<OrderItem> getordersByBusiness(Long businessId) {
        return orderItemRepo.findRichByBusinessId(businessId);
    }

    /**
     * Business marks order as paid (COMPLETED).
     *
     * Typical usage:
     * - CASH payments
     * - Manual reconciliation
     * - Admin overrides
     *
     * Stripe note:
     * - In Stripe flow, you should prefer webhook to mark paid automatically.
     */
    @Override
    public void markPaid(Long orderItemId, Long businessId) {

        OrderItem oi = orderItemRepo.findByIdAndBusiness(orderItemId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found"));

        Order order = oi.getOrder();
        if (order == null) throw new IllegalStateException("Missing order");

        // Resolve tenant
        Long ownerProjectId = oi.getItem().getOwnerProject().getId();

        BigDecimal orderTotal = order.getTotalPrice();

        // 🔎 Check current payment summary
        var summary = paymentRead.summaryForOrder(order.getId(), orderTotal);
        BigDecimal remaining = summary.getRemainingAmount();

        // 💰 Write PAID transaction if needed
        if (remaining.signum() > 0) {
            try {
                
                paymentWrite.markCashAsPaid(order.getId(), orderTotal);
            } catch (NoSuchElementException ex) {
                // ✅ fallback: إذا ما في CASH tx (legacy orders), اعملي manual paid
                paymentWrite.recordManualPaid(
                        ownerProjectId,
                        order.getId(),
                        remaining,
                        order.getCurrency().getCode(),
                        "CASH",
                        "CASH_ORDER_" + order.getId()
                );
            }
        }

        // 🔁 Re-check AFTER ledger update
        var after = paymentRead.summaryForOrder(order.getId(), orderTotal);
        if (!after.isFullyPaid()) {
            throw new IllegalStateException(
                    "Cannot complete order: still not fully paid"
            );
        }

        // ✅ Now COMPLETED is allowed
        order.setStatus(requireStatus("COMPLETED"));
        order.setOrderDate(LocalDateTime.now());

        orderRepo.save(order);
    }

    /**
     * Deletes all orderItems for a specific item.
     * Used when deleting an item/product/activity from catalog.
     */
    @Override
    public void deleteordersByItemId(Long itemId) {
        orderItemRepo.deleteByItem_Id(itemId);
    }

    /**
     * Business rejects an order (custom rule).
     */
    @Override
    public void rejectorder(Long orderItemId, Long businessId) {
        var oi = requireBusinessOwned(orderItemId, businessId);
        Order order = oi.getOrder();
        String curr = currentStatusCode(order);

        if ("COMPLETED".equals(curr)) throw new IllegalStateException("Cannot reject completed");
        if ("REJECTED".equals(curr)) return;

        var ps = paymentRead.summaryForOrder(order.getId(), order.getTotalPrice());
        if (!ps.isFullyPaid()) {
            Order full = orderRepo.findByIdWithItems(order.getId()).orElseThrow();
            releaseStockForOrder(full);
            paymentWrite.recordPaymentFailedOrCanceled(order.getId(), providerForOrder(order), "BUSINESS_REJECTED");

        }

        flipStatus(oi, "REJECTED");
    }

    /**
     * Business restores rejected order back to PENDING (only if not completed).
     */
    @Override
    public void unrejectorder(Long orderItemId, Long businessId) {
        var oi = requireBusinessOwned(orderItemId, businessId);
        String curr = currentStatusCode(oi.getOrder());

        if ("COMPLETED".equals(curr))
            throw new IllegalStateException("Cannot restore completed order");

        flipStatus(oi, "PENDING");
    }

    /* ===============================
       GENERIC CHECKOUT (Ecommerce + Activities)
       ✅ NEW FLOW: create order -> start payment -> return clientSecret
       =============================== */

    /**
     * Generic checkout entry point called by:
     * POST /api/orders/checkout
     *
     * New behavior:
     * 1) Validate request
     * 2) Load items + capacity/stock checks
     * 3) Run pricing engine (shipping + tax + coupon)
     * 4) Create order header + order items (PENDING)
     * 5) Start payment using PaymentOrchestratorService
     * 6) Return CheckoutSummaryResponse with payment info (clientSecret, publishableKey, ...)
     * 7) Clear/convert cart AFTER payment start succeeds
     *
     * Stripe important note:
     * - Client needs BOTH:
     *   (1) publishableKey (pk_...) to initialize Stripe SDK
     *   (2) clientSecret (pi_..._secret_...) to confirm the payment
     */
    @Override
    public CheckoutSummaryResponse checkout(Long userId, CheckoutRequest request) {

        // Basic validation
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (request == null || request.getLines() == null || request.getLines().isEmpty())
            throw new IllegalArgumentException("Cart lines are required");
        if (request.getCurrencyId() == null)
            throw new IllegalArgumentException("currencyId is required");
        if (request.getPaymentMethod() == null || request.getPaymentMethod().isBlank())
            throw new IllegalArgumentException("paymentMethod is required");
        
        

        // Normalize payment method code (STRIPE/CASH/PAYPAL...)
        // We store codes in DB and process them in uppercase consistently.
        String paymentMethodCode = request.getPaymentMethod().trim().toUpperCase(Locale.ROOT);

        // Load user (the buyer)
        // Must come from authenticated session/JWT in controller.
        Users user = usersRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Load currency entity (USD/LBP/SAR...)
        Currency currency = currencyRepo.findById(request.getCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException("Currency not found"));

        // Lines represent cart items coming from client UI.
        // Server must still validate everything (existence, ownership, stock, prices).
        List<CartLine> lines = request.getLines();

        // ---- Load items once, compute unitPrice/lineSubtotal, check capacity
        // itemCache avoids hitting DB again when creating OrderItems.
        Map<Long, Item> itemCache = new HashMap<>();

        // ownerProjectId (tenant) is derived from items to ensure:
        // - All lines belong to the same generated app
        // - We price and pay using the correct tenant configurations
        Long ownerProjectId = null;

        for (CartLine line : lines) {
            if (line.getItemId() == null)
                throw new IllegalArgumentException("itemId is required in cart line");
            if (line.getQuantity() <= 0)
                throw new IllegalArgumentException("quantity must be > 0 for itemId = " + line.getItemId());

            // Fetch item and cache it
            Item item = itemRepo.findById(line.getItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + line.getItemId()));
            itemCache.put(item.getId(), item);

            // Tenant check:
            // Each item belongs to an ownerProject (generated app).
            // Checkout MUST be single-tenant; mixing items across apps is not allowed.
            if (item.getOwnerProject() == null || item.getOwnerProject().getId() == null)
                throw new IllegalStateException("Item " + item.getId() + " has no ownerProject");

            Long opId = item.getOwnerProject().getId();
            if (ownerProjectId == null) ownerProjectId = opId;
            else if (!ownerProjectId.equals(opId))
                throw new IllegalArgumentException("All cart items must belong to the same app (ownerProjectId)");

            // capacity / stock check:
            // - activities: seats/participants
            // - ecommerce: stock
            // We check COMPLETED orders only (current rule).
            Integer capacity = tryCapacity(item);
            if (capacity != null) {
                int already = orderItemRepo.sumQuantityByItemIdAndStatusNames(
                        item.getId(), List.of("COMPLETED")
                );
                int remaining = capacity - already;
                if (line.getQuantity() > remaining)
                    throw new IllegalStateException("Not enough quantity available for itemId = " + item.getId());
            }

            // Price calculation on server:
            // - We set unitPrice + lineSubtotal on the line object
            // - Pricing service uses these fields to compute totals safely
            BigDecimal unit = resolveUnitPrice(item);
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(line.getQuantity()));
            line.setUnitPrice(unit);
            line.setLineSubtotal(lineTotal);
        }

        if (ownerProjectId == null)
            throw new IllegalArgumentException("Could not resolve ownerProjectId from cart items");

        // ---- Prepare shipping address entities (for persisting on Order)
        // The request includes DTO ids (countryId/regionId); we translate them into entities.
        ShippingAddressDTO addr = request.getShippingAddress();
        Country shippingCountry = null;
        Region shippingRegion = null;

        if (addr != null) {
            if (addr.getCountryId() != null) {
                shippingCountry = countryRepo.findById(addr.getCountryId())
                        .orElseThrow(() -> new IllegalArgumentException("Shipping country not found"));
            }
            if (addr.getRegionId() != null) {
                shippingRegion = regionRepo.findById(addr.getRegionId())
                        .orElseThrow(() -> new IllegalArgumentException("Shipping region not found"));
            }
            
            
            
        }
        
        String shippingName = (addr.getFullName() != null) ? addr.getFullName().trim() : "";
        if (shippingName.isBlank()) {
            String fn = user.getFirstName() == null ? "" : user.getFirstName().trim();
            String ln = user.getLastName() == null ? "" : user.getLastName().trim();
            String full = (fn + " " + ln).trim();
            shippingName = full.isBlank() ? String.valueOf(user.getUsername()) : full;
        }
     

        // ---- Delegate full pricing (shipping + tax + coupon) to CheckoutPricingService
        // This is the single place where totals are computed consistently.
        CheckoutSummaryResponse priced = checkoutPricingService.priceCheckout(
                ownerProjectId,
                request.getCurrencyId(),
                request
        );

     // ✅ Consume coupon usage (max uses) atomically
        String couponCode = priced.getCouponCode(); // IMPORTANT: use priced, not request
        if (couponCode != null && !couponCode.isBlank()) {
            couponService.consumeOrThrow(ownerProjectId, couponCode);
        }
        
        // Attach PaymentMethod entity on order header
        // Ensures order stores which payment method was selected in checkout UI.
        PaymentMethod pmEntity = paymentMethodRepo.findByNameIgnoreCase(paymentMethodCode)
                .orElseThrow(() -> new IllegalArgumentException("Payment method not found in platform: " + paymentMethodCode));

        
        
        // ---- Create Order header using priced totals ----
        // We always create the order BEFORE payment (server-driven flow).
        Order order = new Order();
        order.setUser(user);
        order.setStatus(requireStatus("PENDING"));
        order.setOrderDate(LocalDateTime.now());
        order.setCurrency(currency);

        // Important:
        // - totalPrice MUST come from server pricing
        // - never trust totals from client
        order.setTotalPrice(priced.getGrandTotal());

        // Link selected payment method
        order.setPaymentMethod(pmEntity);

        // Shipping fields (persist selection & address)
        if (addr != null) {
            order.setShippingCountry(shippingCountry);
            order.setShippingRegion(shippingRegion);
            order.setShippingCity(addr.getCity());
            order.setShippingPostalCode(addr.getPostalCode());
            order.setShippingMethodId(addr.getShippingMethodId());
            order.setShippingMethodName(addr.getShippingMethodName());
            order.setShippingAddress(addr.getAddressLine());
            order.setShippingPhone(addr.getPhone());
            order.setShippingFullName(shippingName);
        }

        // Pricing totals saved on header (auditing/reporting)
        order.setShippingTotal(priced.getShippingTotal());
        order.setItemTaxTotal(priced.getItemTaxTotal());
        order.setShippingTaxTotal(priced.getShippingTaxTotal());
        order.setCouponCode(priced.getCouponCode());
        order.setCouponDiscount(priced.getCouponDiscount());

        // Save order header first:
        // - We need orderId to create PaymentTransaction (foreign key / reference)
        order = orderRepo.save(order);

        // ---- Create OrderItem lines ----
        // Each CartLine becomes an OrderItem row in DB.
        for (CartLine line : lines) {
            Item item = itemCache.get(line.getItemId());
            if (item == null) {
                // Safety fallback (should not happen due to caching earlier)
                item = itemRepo.findById(line.getItemId())
                        .orElseThrow(() -> new IllegalArgumentException("Item not found: " + line.getItemId()));
            }

            OrderItem oi = new OrderItem();
            oi.setOrder(order);
            oi.setItem(item);
            oi.setUser(user);
            oi.setCurrency(currency);
            oi.setQuantity(line.getQuantity());

            // Price must match what we computed for checkout pricing
            oi.setPrice(line.getUnitPrice());

            // Timestamps
            oi.setCreatedAt(LocalDateTime.now());
            oi.setUpdatedAt(LocalDateTime.now());

            orderItemRepo.save(oi);
        }

        // ✅ NEW: Start payment using PaymentOrchestratorService
        // For Stripe:
        // - Creates PaymentIntent on Stripe
        // - Returns clientSecret for the client to confirm payment
        // - Returns publishableKey (pk_...) from tenant configJson so client can init Stripe SDK
        StartPaymentResponse pay = paymentOrchestrator.startPayment(
                ownerProjectId,
                order.getId(),
                paymentMethodCode,
                priced.getGrandTotal(),
                currency.getCode(),
                request.getDestinationAccountId()
        );

        // Put payment info into checkout response (Flutter uses it)
        priced.setPaymentTransactionId(pay.getTransactionId());
        priced.setPaymentProviderCode(pay.getProviderCode());
        priced.setProviderPaymentId(pay.getProviderPaymentId());
        priced.setClientSecret(pay.getClientSecret());

        // ✅ IMPORTANT:
        // Stripe SDK cannot confirm payment unless it is initialized with publishableKey.
        // Since Build4All is multi-tenant, we must return the tenant key from DB configJson.
        priced.setPublishableKey(pay.getPublishableKey());

        priced.setRedirectUrl(pay.getRedirectUrl());
        priced.setPaymentStatus(pay.getStatus());

        // Attach order id/date to response (UI shows order number and creation timestamp)
        priced.setOrderId(order.getId());
        priced.setOrderDate(order.getOrderDate());

        // ✅ Clear cart ONLY AFTER payment start succeeded
        // This prevents losing the cart if:
        // - Stripe call fails
        // - config is missing
        // - provider returns error
        Cart cart = cartRepo.findByUser_IdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active cart"));

        cart.setStatus(CartStatus.CONVERTED);

        // Remove cart items:
        // - delete rows
        // - clear in-memory collection
        // - recalc totals
        cartItemRepo.deleteByCart_Id(cart.getId());
        cart.getItems().clear();
        recalcTotals(cart);

        cartRepo.save(cart);

        return priced;
    }

    /**
     * Recomputes cart totals after removing items.
     *
     * Why this exists:
     * - Cart entity stores a denormalized totalPrice for fast rendering
     * - After deleting items, we re-sync totals to 0
     *
     * Note:
     * - During checkout we delete cart items, so total should become 0
     */
    private void recalcTotals(Cart cart) {
        if (cart == null) return;
        BigDecimal total = BigDecimal.ZERO;
        if (cart.getItems() != null) {
            for (CartItem ci : cart.getItems()) {
                BigDecimal unit = ci.getUnitPrice() == null ? BigDecimal.ZERO : ci.getUnitPrice();
                total = total.add(unit.multiply(BigDecimal.valueOf(ci.getQuantity())));
            }
        }
        cart.setTotalPrice(total);
        cart.setUpdatedAt(LocalDateTime.now());
    }
    
    public CheckoutSummaryResponse checkoutFromCart(Long userId, CheckoutRequest request) {

        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (request == null) throw new IllegalArgumentException("request is required");
        if (request.getCurrencyId() == null) throw new IllegalArgumentException("currencyId is required");
        if (request.getPaymentMethod() == null || request.getPaymentMethod().isBlank())
            throw new IllegalArgumentException("paymentMethod is required");

        // ✅ Load ACTIVE cart from DB (server decides lines)
        Cart cart = cartRepo.findByUser_IdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active cart"));

        if (cart.getItems() == null || cart.getItems().isEmpty())
            throw new IllegalStateException("Cart is empty");

        // ✅ Aggregate by itemId (prevents bypass via duplicates)
        Map<Long, Integer> qtyByItemId = new LinkedHashMap<>();
        for (CartItem ci : cart.getItems()) {
            if (ci.getItem() == null || ci.getItem().getId() == null) continue;
            int qty = ci.getQuantity();
            if (qty <= 0) continue;

            Long itemId = ci.getItem().getId();
            qtyByItemId.merge(itemId, qty, Integer::sum);
        }

        if (qtyByItemId.isEmpty())
            throw new IllegalStateException("Cart has no valid items");

        // Build server-trusted lines
        List<CartLine> lines = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : qtyByItemId.entrySet()) {
            CartLine line = new CartLine();
            line.setItemId(e.getKey());
            line.setQuantity(e.getValue());
            lines.add(line);
        }

        // ✅ Gate: stock/capacity + tenant isolation (LOCK items)
        List<String> blockingErrors = new ArrayList<>();
        List<Map<String, Object>> lineErrors = new ArrayList<>();

        Long ownerProjectId = null;

        // statuses that reserve seats (activities)
        List<String> reservedStatuses = List.of("PENDING", "COMPLETED", "CANCEL_REQUESTED");

        for (CartLine line : lines) {

            if (line.getItemId() == null) continue;

            // lock item row for stock/capacity check
            Item fresh = itemRepo.findByIdForStockCheck(line.getItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + line.getItemId()));

            // tenant scope (single app only)
            if (fresh.getOwnerProject() == null || fresh.getOwnerProject().getId() == null) {
                blockingErrors.add("Item " + fresh.getId() + " has no ownerProject");
                lineErrors.add(Map.of(
                        "itemId", fresh.getId(),
                        "reason", "NO_TENANT",
                        "message", "Item has no ownerProject"
                ));
                continue;
            }

            Long opId = fresh.getOwnerProject().getId();
            if (ownerProjectId == null) ownerProjectId = opId;
            else if (!ownerProjectId.equals(opId)) {
                blockingErrors.add("Cart contains items from multiple apps (tenant mix)");
                lineErrors.add(Map.of(
                        "itemId", fresh.getId(),
                        "reason", "TENANT_MISMATCH",
                        "message", "Item belongs to a different app"
                ));
                continue;
            }

            int qty = line.getQuantity();
            if (qty <= 0) {
                blockingErrors.add("Invalid quantity for item " + fresh.getId());
                lineErrors.add(Map.of(
                        "itemId", fresh.getId(),
                        "reason", "INVALID_QTY",
                        "message", "Quantity must be > 0"
                ));
                continue;
            }

            // ✅ STOCK first (ecommerce)
            Integer stock = readStock(fresh);
            if (stock != null) {
                if (stock <= 0) {
                    blockingErrors.add("Item " + fresh.getId() + " is out of stock");
                    lineErrors.add(Map.of(
                            "itemId", fresh.getId(),
                            "reason", "OUT_OF_STOCK",
                            "availableStock", stock,
                            "maxAllowedQuantity", 0,
                            "message", "Out of stock"
                    ));
                } else if (qty > stock) {
                    blockingErrors.add("Only " + stock + " left for item " + fresh.getId());
                    lineErrors.add(Map.of(
                            "itemId", fresh.getId(),
                            "reason", "QTY_EXCEEDS_STOCK",
                            "availableStock", stock,
                            "maxAllowedQuantity", stock,
                            "message", "Only " + stock + " left"
                    ));
                }
                continue; // stock handled
            }

            // ✅ CAPACITY (activities)
            Integer capacity = readSeatsCapacity(fresh);
            if (capacity != null) {
                int alreadyReserved = orderItemRepo.sumQuantityByItemIdAndStatusNames(fresh.getId(), reservedStatuses);
                int remaining = capacity - alreadyReserved;

                if (remaining <= 0) {
                    blockingErrors.add("No seats left for item " + fresh.getId());
                    lineErrors.add(Map.of(
                            "itemId", fresh.getId(),
                            "reason", "NO_CAPACITY",
                            "availableStock", 0,
                            "maxAllowedQuantity", 0,
                            "message", "No seats left"
                    ));
                } else if (qty > remaining) {
                    blockingErrors.add("Only " + remaining + " seats left for item " + fresh.getId());
                    lineErrors.add(Map.of(
                            "itemId", fresh.getId(),
                            "reason", "QTY_EXCEEDS_CAPACITY",
                            "availableStock", remaining,
                            "maxAllowedQuantity", remaining,
                            "message", "Only " + remaining + " left"
                    ));
                }
            }
        }

        if (!blockingErrors.isEmpty()) {
            throw new com.build4all.order.web.CheckoutBlockedException(blockingErrors, lineErrors);
        }
        
     // ✅ After validation success (no blockingErrors)
     // Reserve stock for ecommerce items (atomic)
     // ✅ Reserve stock (atomic) for stock-tracked items
        for (CartLine line : lines) {
            if (line.getItemId() == null) continue;

            Item fresh = itemRepo.findByIdForStockCheck(line.getItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + line.getItemId()));

            Integer stock = readStock(fresh); // ✅ only stock-tracked items
            if (stock != null) {
                int qty = line.getQuantity();

                int updated = itemRepo.decrementStockIfEnough(fresh.getId(), qty);

                if (updated != 1) {
                    throw new com.build4all.order.web.CheckoutBlockedException(
                            List.of("Stock changed for item " + fresh.getId() + ". Please refresh cart."),
                            List.of(Map.of(
                                    "itemId", fresh.getId(),
                                    "reason", "STOCK_CHANGED",
                                    "message", "Stock changed, please refresh"
                            ))
                    );
                }
            }

        }

     

        // ✅ Override lines from server cart (ignore client lines)
        request.setLines(lines);

        // Run existing checkout pipeline (pricing + order + payment + cart clear)
        return checkout(userId, request);
    }


    /* -------------------- helpers (stock/capacity detection) -------------------- */

    private Integer readStock(Item item) {
        // if returns null => "not stock-tracked"
        Object v = tryGet(item, "getStock", "getAvailableStock", "getQuantity", "getAvailableQuantity");
        if (v == null) return null;

        if (v instanceof Integer i) return i;
        if (v instanceof Long l) return l.intValue();
        if (v instanceof Number n) return n.intValue();

        try { return Integer.parseInt(v.toString()); }
        catch (Exception e) { return null; }
    }
    
    private void releaseStockForOrder(Order order) {
        if (order == null || order.getOrderItems() == null) return;

        for (OrderItem oi : order.getOrderItems()) {
            Item item = oi.getItem();
            if (item == null || item.getId() == null) continue;

            Integer stock = readStock(item);
            if (stock == null) continue;

            int qty = oi.getQuantity();
            if (qty > 0) {
                itemRepo.incrementStock(item.getId(), qty);
            }
        }
    }


    /**
     * Capacity for activities: seats/participants.
     * IMPORTANT: we do NOT include getStock here, because stock is handled separately above.
     */
    private Integer readSeatsCapacity(Item item) {
        String[] names = {"getMaxParticipants", "getCapacity", "getSeats", "getQuantityLimit"};
        for (String n : names) {
            try {
                Method m = item.getClass().getMethod(n);
                Object v = m.invoke(item);
                if (v instanceof Integer i) return i;
                if (v instanceof Number x) return x.intValue();
            } catch (Exception ignored) {}
        }
        return null;
    }
    


    private Object tryGet(Object target, String... candidates) {
        if (target == null) return null;
        Class<?> c = target.getClass();
        for (String name : candidates) {
            try {
                Method m = c.getMethod(name);
                return m.invoke(target);
            } catch (Exception ignored) {}
        }
        return null;
    }



   

    private void assertStockAvailable(Item item, int desiredQty) {
        if (desiredQty <= 0) throw new IllegalArgumentException("quantity must be > 0");

        Integer stock = readStock(item);
        if (stock == null) return; // not enforced

        if (stock <= 0) throw new IllegalStateException("Out of stock");
        if (desiredQty > stock) throw new IllegalStateException("Only " + stock + " left");
    }
    
    
    @Override
    public void failCashOrder(Long orderItemId, Long businessId, String reason) {

        OrderItem oi = requireBusinessOwned(orderItemId, businessId);
        Order order = oi.getOrder();
        if (order == null) throw new IllegalStateException("Missing order");

        String curr = currentStatusCode(order);
        if ("COMPLETED".equals(curr)) throw new IllegalStateException("Cannot fail a completed order");
        if ("CANCELED".equals(curr) || "REJECTED".equals(curr) || "EXPIRED".equals(curr)) return;

        // ✅ if not paid -> release stock
        var ps = paymentRead.summaryForOrder(order.getId(), order.getTotalPrice());
        if (!ps.isFullyPaid()) {
            Order full = orderRepo.findByIdWithItems(order.getId())
                    .orElseThrow(() -> new IllegalStateException("Order not found"));
            releaseStockForOrder(full);

            paymentWrite.recordPaymentFailedOrCanceled(
                    order.getId(),
                    "CASH",
                    (reason == null || reason.isBlank()) ? "NOT_PAID" : reason
            );
        }

   
        flipStatus(oi, "CANCELED");
    }

    
    private String providerForOrder(Order order) {
        if (order != null && order.getPaymentMethod() != null && order.getPaymentMethod().getName() != null) {
            return order.getPaymentMethod().getName().trim().toUpperCase(Locale.ROOT);
        }
        return "CASH";
    }
    
    
    private Long resolveOwnerProjectIdFromOrder(Order order) {
        if (order == null || order.getOrderItems() == null) return null;
        for (OrderItem oi : order.getOrderItems()) {
            if (oi == null || oi.getItem() == null || oi.getItem().getOwnerProject() == null) continue;
            if (oi.getItem().getOwnerProject().getId() != null) return oi.getItem().getOwnerProject().getId();
        }
        return null;
    }

    private void releaseCouponForOrderIfAny(Order order) {
        if (order == null) return;
        String code = order.getCouponCode();
        if (code == null || code.isBlank()) return;

        Long ownerProjectId = resolveOwnerProjectIdFromOrder(order);
        if (ownerProjectId == null) return;

        couponService.releaseOne(ownerProjectId, code);
    }



}
