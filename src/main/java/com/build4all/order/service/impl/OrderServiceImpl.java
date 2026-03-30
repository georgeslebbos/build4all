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
import com.build4all.common.errors.ApiException;
import com.build4all.notifications.service.FrontAppNotificationService;
import com.build4all.order.domain.Order;
import com.build4all.order.domain.OrderItem;
import com.build4all.order.domain.OrderSequence;
import com.build4all.order.domain.OrderStatus;
import com.build4all.order.dto.CartLine;
import com.build4all.order.dto.CheckoutRequest;
import com.build4all.order.dto.CheckoutSummaryResponse;
import com.build4all.order.dto.OrderEditRequest;
import com.build4all.order.dto.ShippingAddressDTO;
import com.build4all.order.repository.OrderItemRepository;
import com.build4all.order.repository.OrderRepository;
import com.build4all.order.repository.OrderSequenceRepository;
import com.build4all.order.repository.OrderStatusRepository;
import com.build4all.order.service.CheckoutPricingService;
import com.build4all.order.service.OrderService;
import com.build4all.payment.domain.PaymentMethod;
import com.build4all.payment.dto.StartPaymentResponse;
import com.build4all.payment.repository.PaymentMethodRepository;
import com.build4all.payment.service.OrderPaymentReadService;
import com.build4all.payment.service.OrderPaymentWriteService;
import com.build4all.payment.service.PaymentOrchestratorService;
import com.build4all.promo.domain.Coupon;
import com.build4all.promo.domain.CouponDiscountType;
import com.build4all.promo.service.CouponService;
import com.build4all.user.domain.Users;
import com.build4all.user.repository.UsersRepository;
import com.build4all.webSocket.service.WebSocketEventService;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderItemRepository orderItemRepo;
    private final OrderRepository orderRepo;
    private final UsersRepository usersRepo;
    private final ItemRepository itemRepo;
    private final CurrencyRepository currencyRepo;
    private final OrderStatusRepository orderStatusRepo;
    private final WebSocketEventService wsEvents;
    private final FrontAppNotificationService frontAppNotificationService;
    private final OrderPaymentReadService paymentRead;
    private final OrderPaymentWriteService paymentWrite;

    private final CountryRepository countryRepo;
    private final RegionRepository regionRepo;

    private final CouponService couponService;

    private final CartItemRepository cartItemRepo;
    private final OrderSequenceRepository orderSeqRepo;

    private final CheckoutPricingService checkoutPricingService;
    private final CartRepository cartRepo;

    private final PaymentOrchestratorService paymentOrchestrator;
    private final PaymentMethodRepository paymentMethodRepo;

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
            CouponService couponService,
            OrderSequenceRepository orderSeqRepo,
            WebSocketEventService wsEvents,
            FrontAppNotificationService frontAppNotificationService
    ) {
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
        this.couponService = couponService;
        this.orderSeqRepo = orderSeqRepo;
        this.wsEvents = wsEvents;
        this.frontAppNotificationService = frontAppNotificationService;
    }

    
    private static final int LOW_STOCK_THRESHOLD = 5;
    /* ===============================
       STATUS HELPERS
       =============================== */
    
    private String mapCouponErrorMessage(Exception ex) {
        if (ex instanceof ApiException apiEx) {
            return switch (apiEx.getCode()) {
                case "COUPON_EXPIRED" -> "Coupon was not applied because it is expired";
                case "COUPON_USAGE_LIMIT_REACHED" -> "Coupon was not applied because it reached the usage limit";
                case "COUPON_INVALID" -> "Coupon was not applied because it is invalid";
                case "COUPON_MINIMUM_NOT_REACHED" -> "Coupon was not applied because order minimum was not reached";
                case "COUPON_INACTIVE" -> "Coupon was not applied because it is inactive";
                default -> "Coupon was not applied";
            };
        }

        String msg = ex == null || ex.getMessage() == null
                ? ""
                : ex.getMessage().trim().toLowerCase(Locale.ROOT);

        if (msg.contains("expired")) {
            return "Coupon was not applied because it is expired";
        }
        if ((msg.contains("max") && msg.contains("use")) || msg.contains("usage limit")) {
            return "Coupon was not applied because it reached the usage limit";
        }
        if (msg.contains("inactive")) {
            return "Coupon was not applied because it is inactive";
        }
        if (msg.contains("not found") || msg.contains("invalid")) {
            return "Coupon was not applied because it is invalid";
        }
        if (msg.contains("minimum")) {
            return "Coupon was not applied because order minimum was not reached";
        }

        return "Coupon was not applied";
    }
    

    private Long resolveOwnerUserId(Long ownerProjectId) {
        if (ownerProjectId == null || ownerProjectId <= 0) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }

        // TEMPORARY FOR CURRENT TEST TENANT:
        // your current runtime logs show linkId=1 and ownerId=1,
        // so this works for testing only.
        return ownerProjectId;
    }
    
    
    private String resolveItemDisplayName(Item item) {
        if (item == null) return "Item";

        Object v = tryGet(item, "getName", "getItemName", "getTitle");
        if (v == null) return "Item";

        String s = v.toString().trim();
        return s.isEmpty() ? "Item" : s;
    }

    private void notifyOwnerStockAlertIfNeeded(Item item, Integer newStock) {
        if (item == null || item.getId() == null || newStock == null) return;
        if (item.getOwnerProject() == null || item.getOwnerProject().getId() == null) return;

        Long ownerProjectId = item.getOwnerProject().getId();
        Long ownerUserId = resolveOwnerUserId(ownerProjectId);
        String itemName = resolveItemDisplayName(item);

        try {
            if (newStock <= 0) {
                frontAppNotificationService.notifyOwnerOutOfStock(
                        ownerProjectId,
                        ownerUserId,
                        item.getId(),
                        itemName
                );
            } else if (newStock <= LOW_STOCK_THRESHOLD) {
                frontAppNotificationService.notifyOwnerLowStock(
                        ownerProjectId,
                        ownerUserId,
                        item.getId(),
                        itemName,
                        newStock
                );
            }
        } catch (Exception e) {
            System.out.println("Stock changed, but failed to send owner stock notification => " + e.getMessage());
        }
    }
    private Long resolveOrderUserId(Order order) {
        if (order == null || order.getUser() == null || order.getUser().getId() == null) {
            throw new IllegalStateException("Order user is missing");
        }
        return order.getUser().getId();
    }

    private Long resolveOwnerProjectId(OrderItem oi) {
        if (oi == null || oi.getItem() == null || oi.getItem().getOwnerProject() == null
                || oi.getItem().getOwnerProject().getId() == null) {
            throw new IllegalStateException("Owner project id is missing on order item");
        }
        return oi.getItem().getOwnerProject().getId();
    }

    private void notifyUserOrderRejectedSafe(Order order, Long ownerProjectId, String reason) {
        try {
            Long ownerUserId = resolveOwnerUserId(ownerProjectId);
            Long userId = resolveOrderUserId(order);

            frontAppNotificationService.notifyUserOrderRejected(
                    ownerProjectId,
                    ownerUserId,
                    userId,
                    order.getId(),
                    order.getOrderCode(),
                    reason
            );
        } catch (Exception e) {
            System.out.println("Order rejected, but failed to send user notification => " + e.getMessage());
        }
    }

    private void notifyUserOrderCanceledByOwnerSafe(Order order, Long ownerProjectId, String reason) {
        try {
            Long ownerUserId = resolveOwnerUserId(ownerProjectId);
            Long userId = resolveOrderUserId(order);

            frontAppNotificationService.notifyUserOrderCanceledByOwner(
                    ownerProjectId,
                    ownerUserId,
                    userId,
                    order.getId(),
                    order.getOrderCode(),
                    reason
            );
        } catch (Exception e) {
            System.out.println("Order canceled by owner, but failed to send user notification => " + e.getMessage());
        }
    }

    private void notifyUserOrderStatusUpdatedSafe(Order order, Long ownerProjectId, String statusCode) {
        try {
            Long ownerUserId = resolveOwnerUserId(ownerProjectId);
            Long userId = resolveOrderUserId(order);

            frontAppNotificationService.notifyUserOrderStatusUpdated(
                    ownerProjectId,
                    ownerUserId,
                    userId,
                    order.getId(),
                    order.getOrderCode(),
                    statusCode
            );
        } catch (Exception e) {
            System.out.println("Order status updated, but failed to send user notification => " + e.getMessage());
        }
    }
    private OrderStatus requireStatus(String code) {
        return orderStatusRepo.findByNameIgnoreCase(code)
                .orElseThrow(() -> new IllegalStateException("OrderStatus not found: " + code));
    }

    private String currentStatusCode(Order header) {
        if (header == null || header.getStatus() == null || header.getStatus().getName() == null) return "";
        return header.getStatus().getName().toUpperCase(Locale.ROOT);
    }

    /**
     * ✅ FIX:
     * - DO NOT update header.orderDate (createdAt) when flipping status.
     * - Only update the line updatedAt (or rely on PreUpdate if you added it).
     */
    private void flipStatus(OrderItem oi, String newStatusUpper) {
        Order header = oi.getOrder();
        if (header == null) throw new IllegalStateException("Missing order header");

        OrderStatus statusEntity = requireStatus(newStatusUpper);
        header.setStatus(statusEntity);

        // ✅ do NOT touch header.setOrderDate(now)
        oi.setUpdatedAt(LocalDateTime.now());

        orderRepo.save(header);
        orderItemRepo.save(oi);
    }

    /* ===============================
       PRICING HELPERS
       =============================== */

    private BigDecimal resolveUnitPrice(Item item) {
        if (item == null) return BigDecimal.ZERO;

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

    private Integer tryCapacity(Item item) {
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

    
    
    private OrderItem requireUserOwned(Long orderItemId, Long userId) {
        return orderItemRepo.findByIdAndUser(orderItemId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found or not yours"));
    }

    private OrderItem requireBusinessOwned(Long orderItemId, Long businessId) {
        return orderItemRepo.findByIdAndBusiness(orderItemId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found or not your business"));
    }

    
    /* ===============================
       LEGACY CREATE (STRIPE)
       =============================== */

    @Override
    public OrderItem createBookItem(Long userId, Long itemId, int quantity, String stripePaymentId, Long currencyId) {

        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (itemId == null) throw new IllegalArgumentException("itemId is required");
        if (quantity <= 0) throw new IllegalArgumentException("participants must be > 0");
        if (stripePaymentId == null || stripePaymentId.isBlank())
            throw new IllegalArgumentException("stripePaymentId is required");

        try {
            var pi = com.stripe.model.PaymentIntent.retrieve(stripePaymentId);
            if (pi == null || !"succeeded".equalsIgnoreCase(pi.getStatus()))
                throw new IllegalStateException("Stripe payment not confirmed");
        } catch (Exception e) {
            throw new IllegalStateException("Stripe error: " + e.getMessage());
        }

        Users user = usersRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        requireActiveUser(user);

        Item item = itemRepo.findByIdForStockCheck(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
        assertItemPurchasable(item);

        
        Currency currency = null;
        if (currencyId != null) {
            currency = currencyRepo.findById(currencyId)
                    .orElseThrow(() -> new IllegalArgumentException("Currency not found"));
        }

        Integer capacity = tryCapacity(item);
        if (capacity != null) {
            List<String> reservedStatuses = List.of("PENDING", "COMPLETED", "CANCEL_REQUESTED");
            int already = orderItemRepo.sumQuantityByItemIdAndStatusNames(item.getId(), reservedStatuses);
            int remaining = capacity - already;

            if (quantity > remaining) throw new IllegalStateException("Not enough seats available");
        }

        BigDecimal unit = resolveUnitPrice(item);
        BigDecimal total = unit.multiply(BigDecimal.valueOf(quantity));

        Order order = new Order();
        order.setUser(user);
        order.setStatus(requireStatus("PENDING"));

        // ✅ keep as creation time (if you later add createdAt, this can be removed)
        order.setOrderDate(LocalDateTime.now());

        order.setTotalPrice(total);
        if (currency != null) order.setCurrency(currency);
        order = orderRepo.save(order);

        OrderItem line = new OrderItem();
        line.setOrder(order);
        line.setItem(item);
        line.setUser(user);
        line.setQuantity(quantity);
        line.setPrice(unit);
        if (currency != null) line.setCurrency(currency);
        line.setCreatedAt(LocalDateTime.now());
        line.setUpdatedAt(LocalDateTime.now());

        return orderItemRepo.save(line);
    }

    
 // Put near other helpers in OrderServiceImpl

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Shipping is required if the order contains stock-based items (physical products).
     * In your codebase, stock != null is the signal for "shippable".
     */
    private boolean isShippingRequired(Item item) {
        return readStock(item) != null; // ✅ physical/shippable
    }

    private void validateShippingOrThrow(ShippingAddressDTO addr, boolean required) {
        if (!required) return;

        if (addr == null) {
            throw new IllegalArgumentException("CHECKOUT_SHIPPING_ADDRESS_REQUIRED");
        }

        // ✅ NEW: require name
        if (isBlank(addr.getFullName())) {
            throw new IllegalArgumentException("CHECKOUT_SHIPPING_FULLNAME_REQUIRED");
        }

        if (addr.getCountryId() == null || addr.getCountryId() <= 0) {
            throw new IllegalArgumentException("CHECKOUT_SHIPPING_COUNTRY_REQUIRED");
        }

        if (isBlank(addr.getCity())) {
            throw new IllegalArgumentException("CHECKOUT_SHIPPING_CITY_REQUIRED");
        }

        if (isBlank(addr.getAddressLine())) {
            throw new IllegalArgumentException("CHECKOUT_SHIPPING_ADDRESSLINE_REQUIRED");
        }

        if (isBlank(addr.getPhone())) {
            throw new IllegalArgumentException("CHECKOUT_SHIPPING_PHONE_REQUIRED");
        }

        if (addr.getShippingMethodId() == null || addr.getShippingMethodId() <= 0) {
            throw new IllegalArgumentException("CHECKOUT_SHIPPING_METHOD_REQUIRED");
        }

        String p = addr.getPhone().trim();
        if (p.length() < 6 || p.length() > 24) {
            throw new IllegalArgumentException("CHECKOUT_SHIPPING_PHONE_INVALID");
        }
    }
    /* ===============================
       LEGACY CREATE (CASH)
       =============================== */

    @Override
    public OrderItem createCashorderByBusiness(Long itemId, Long businessUserId, int quantity, boolean wasPaid, Long currencyId) {

        if (itemId == null) throw new IllegalArgumentException("itemId is required");
        if (businessUserId == null) throw new IllegalArgumentException("businessUserId is required");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");

        Users user = usersRepo.findById(businessUserId)
                .orElseThrow(() -> new IllegalArgumentException("Business user not found"));
        requireActiveUser(user);

        Item item = itemRepo.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));
        assertItemPurchasable(item);
       

        Currency currency = null;
        if (currencyId != null) {
            currency = currencyRepo.findById(currencyId)
                    .orElseThrow(() -> new IllegalArgumentException("Currency not found"));
        }

        Integer capacity = tryCapacity(item);
        if (capacity != null) {
            List<String> reservedStatuses = List.of("PENDING", "COMPLETED", "CANCEL_REQUESTED");
            int already = orderItemRepo.sumQuantityByItemIdAndStatusNames(item.getId(), reservedStatuses);
            int remaining = capacity - already;
            if (quantity > remaining) throw new IllegalStateException("Not enough seats available" + item.getId());
        }

        BigDecimal unit = resolveUnitPrice(item);
        BigDecimal total = unit.multiply(BigDecimal.valueOf(quantity));

        Order order = new Order();
        order.setUser(user);
        order.setStatus(requireStatus("PENDING"));
        order.setOrderDate(LocalDateTime.now());
        order.setTotalPrice(total);
        if (currency != null) order.setCurrency(currency);
        order = orderRepo.save(order);

        OrderItem line = new OrderItem();
        line.setOrder(order);
        line.setItem(item);
        line.setUser(user);
        line.setQuantity(quantity);
        line.setPrice(unit);
        if (currency != null) line.setCurrency(currency);
        line.setCreatedAt(LocalDateTime.now());
        line.setUpdatedAt(LocalDateTime.now());

        return orderItemRepo.save(line);
    }

    /* ===============================
       QUERIES
       =============================== */

    @Override
    public boolean hasUserAlreadyBooked(Long itemId, Long userId) {
        return orderItemRepo.existsByItem_IdAndUser_Id(itemId, userId);
    }

    @Override
    public List<OrderItem> getMyorders(Long userId) {
        return orderItemRepo.findByUser_IdOrderByCreatedAtDesc(userId);
    }

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

            String provider = providerForOrder(order);
            paymentWrite.recordPaymentFailedOrCanceled(order.getId(), provider, "USER_CANCELED");
        }

        flipStatus(oi, "CANCELED");
    }

    @Override
    public void resetToPending(Long orderItemId, Long actorId) {
        var oi = requireUserOwned(orderItemId, actorId);
        var header = oi.getOrder();
        String curr = currentStatusCode(header);

        if ("COMPLETED".equals(curr)) {
            throw new IllegalStateException("Cannot reset a completed order");
        }

        if (isReleasedStatus(curr)) {
            Order full = orderRepo.findByIdWithItems(header.getId())
                    .orElseThrow(() -> new IllegalStateException("Order not found"));
            reserveStockForOrder(full);
        }

        flipStatus(oi, "PENDING");
    }
    @Override
    public void deleteorder(Long orderItemId, Long actorId) {
        orderItemRepo.findByIdAndUser(orderItemId, actorId)
                .or(() -> orderItemRepo.findById(orderItemId))
                .orElseThrow(() -> new IllegalArgumentException("Order item not found or not yours"));
        orderItemRepo.deleteById(orderItemId);
    }

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
                Order full = orderRepo.findByIdWithItems(header.getId())
                        .orElseThrow(() -> new IllegalStateException("Order not found"));

                releaseStockForOrder(full);
                releaseCouponForOrderIfAny(full);

                flipStatus(oi, "CANCELED");
                flipStatus(oi, "REFUNDED");
            }
            default -> throw new IllegalStateException("Unknown status: " + curr);
        }
    }

    @Override
    public void requestCancel(Long orderItemId, Long userId) {
        var oi = requireUserOwned(orderItemId, userId);
        String curr = currentStatusCode(oi.getOrder());

        if ("COMPLETED".equals(curr)) throw new IllegalStateException("Cannot request cancel on a completed order");
        if ("CANCELED".equals(curr)) return;

        flipStatus(oi, "CANCEL_REQUESTED");
    }

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
            releaseCouponForOrderIfAny(full);
            paymentWrite.recordPaymentFailedOrCanceled(order.getId(), providerForOrder(order), "CANCEL_APPROVED");
        }

        flipStatus(oi, "CANCELED");

        Long ownerProjectId = resolveOwnerProjectId(oi);
        notifyUserOrderCanceledByOwnerSafe(order, ownerProjectId, "Cancel request approved");
    }

    @Override
    public void rejectCancel(Long orderItemId, Long businessId) {
        var oi = requireBusinessOwned(orderItemId, businessId);
        Order order = oi.getOrder();
        String curr = currentStatusCode(order);

        if ("CANCELED".equals(curr)) throw new IllegalStateException("Already canceled");
        if ("COMPLETED".equals(curr)) return;

        flipStatus(oi, "PENDING");

        Long ownerProjectId = resolveOwnerProjectId(oi);
        notifyUserOrderStatusUpdatedSafe(order, ownerProjectId, "PENDING");
    }
    
    @Override
    public void markRefunded(Long orderItemId, Long businessId) {
        var oi = requireBusinessOwned(orderItemId, businessId);
        flipStatus(oi, "REFUNDED");
    }

    @Override
    public void ownerRejectOrder(Long orderId, Long ownerProjectId, String reason) {

        if (orderId == null) throw new IllegalArgumentException("orderId is required");
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId is required");

        Order order = orderRepo.findByIdWithItems(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        boolean ok = order.getOrderItems() != null && order.getOrderItems().stream().anyMatch(oi ->
                oi != null
                        && oi.getItem() != null
                        && oi.getItem().getOwnerProject() != null
                        && ownerProjectId.equals(oi.getItem().getOwnerProject().getId())
        );
        if (!ok) throw new NoSuchElementException("Order not found");

        String curr = currentStatusCode(order);
        if ("REJECTED".equals(curr)) return;
        if ("COMPLETED".equals(curr))
            throw new IllegalStateException("Cannot reject a completed order");

        var ps = paymentRead.summaryForOrder(order.getId(), order.getTotalPrice());
        if (!ps.isFullyPaid()) {
            releaseStockForOrder(order);
            releaseCouponForOrderIfAny(order);
            paymentWrite.recordPaymentFailedOrCanceled(
                    order.getId(),
                    providerForOrder(order),
                    (reason == null || reason.isBlank()) ? "OWNER_REJECTED" : reason
            );
        }

        order.setStatus(requireStatus("REJECTED"));
        orderRepo.save(order);

        notifyUserOrderRejectedSafe(order, ownerProjectId, reason);
    }

    @Override
    public List<OrderItem> getordersByBusiness(Long businessId) {
        return orderItemRepo.findRichByBusinessId(businessId);
    }

    @Override
    public void markPaid(Long orderItemId, Long businessId) {
        OrderItem oi = orderItemRepo.findByIdAndBusiness(orderItemId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found"));

        Order order = oi.getOrder();
        if (order == null) throw new IllegalStateException("Missing order");

        String curr = currentStatusCode(order);

        Long ownerProjectId = oi.getItem().getOwnerProject().getId();
        BigDecimal orderTotal = order.getTotalPrice();

        var summary = paymentRead.summaryForOrder(order.getId(), orderTotal);
        BigDecimal remaining = summary.getRemainingAmount();

        if (remaining.signum() > 0) {
            try {
                paymentWrite.markCashAsPaid(order.getId(), orderTotal);
            } catch (NoSuchElementException ex) {
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

        var after = paymentRead.summaryForOrder(order.getId(), orderTotal);
        if (!after.isFullyPaid()) {
            throw new IllegalStateException("Cannot complete order: still not fully paid");
        }

        if (isReleasedStatus(curr)) {
            Order full = orderRepo.findByIdWithItems(order.getId())
                    .orElseThrow(() -> new IllegalStateException("Order not found"));
            reserveStockForOrder(full);
        }

        order.setStatus(requireStatus("COMPLETED"));
        orderRepo.save(order);

        notifyUserOrderStatusUpdatedSafe(order, ownerProjectId, "COMPLETED");
    }
       

    @Override
    public void deleteordersByItemId(Long itemId) {
        orderItemRepo.deleteByItem_Id(itemId);
    }

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

            // ✅ FIX: return coupon too (same as cancel/owner reject)
            releaseCouponForOrderIfAny(full);

            paymentWrite.recordPaymentFailedOrCanceled(order.getId(), providerForOrder(order), "BUSINESS_REJECTED");
        }

        flipStatus(oi, "REJECTED");

        Long ownerProjectId = resolveOwnerProjectId(oi);
        notifyUserOrderRejectedSafe(order, ownerProjectId, "Business rejected the order");
    }

    @Override
    public void unrejectorder(Long orderItemId, Long businessId) {
        var oi = requireBusinessOwned(orderItemId, businessId);
        Order order = oi.getOrder();
        String curr = currentStatusCode(order);

        if ("COMPLETED".equals(curr)) {
            throw new IllegalStateException("Cannot restore completed order");
        }

        if (isReleasedStatus(curr)) {
            Order full = orderRepo.findByIdWithItems(order.getId())
                    .orElseThrow(() -> new IllegalStateException("Order not found"));
            reserveStockForOrder(full);
        }

        flipStatus(oi, "PENDING");

        Long ownerProjectId = resolveOwnerProjectId(oi);
        notifyUserOrderStatusUpdatedSafe(order, ownerProjectId, "PENDING");
    }
    
    private boolean isReleasedStatus(String status) {
        if (status == null) return false;
        String s = status.trim().toUpperCase(Locale.ROOT);
        return "REJECTED".equals(s) || "CANCELED".equals(s) || "REFUNDED".equals(s);
    }
    
    private void requireActiveUser(Users user) {
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            throw new IllegalStateException("User account is inactive or blocked");
        }
    }

    private String itemStatusCode(Item item) {
        if (item == null || item.getStatus() == null || item.getStatus().getCode() == null) return "";
        return item.getStatus().getCode().trim().toUpperCase(Locale.ROOT);
    }

    private void assertItemPurchasable(Item item) {
        if (item == null) {
            throw new IllegalArgumentException("Item not found");
        }

        String code = itemStatusCode(item);
        if (!"PUBLISHED".equals(code)) {
            throw new IllegalStateException("Item is not available for ordering");
        }
    }
    /* ===============================
       ORDER CODE
       =============================== */

    private String appPrefix(Long ownerProjectId, String slug) {
        String s = safeSlug(slug);
        return s.isBlank() ? ("APP" + ownerProjectId) : s;
    }
    
    private String shortKeyFromSlug(String slug) {
        if (slug == null) return "";
        String s = slug.trim().toUpperCase(Locale.ROOT);
        s = s.replaceAll("[^A-Z0-9]+", ""); // remove dashes/spaces
        if (s.length() > 6) s = s.substring(0, 6);
        return s;
    }
    
    private String seqBase36(long seq, int width) {
        String s = Long.toString(seq, 36).toUpperCase(Locale.ROOT);
        if (s.length() >= width) return s;
        return "0".repeat(width - s.length()) + s;
    }
    
    private String safeSlug(String slug) {
        if (slug == null) return "";
        String s = slug.trim().toUpperCase(Locale.ROOT);
        // keep letters/numbers, convert everything else to '-'
        s = s.replaceAll("[^A-Z0-9]+", "-");
        s = s.replaceAll("(^-+|-+$)", ""); // trim '-'
        if (s.isBlank()) return "";
        // optional: limit length so codes stay tidy
        if (s.length() > 20) s = s.substring(0, 20);
        return s;
    }
    
    private String formatoldCode(String prefix, long seq) {
        String yyyymmdd = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        return prefix + "-" + yyyymmdd + "-" + String.format("%06d", seq);
    }

    private String formatCode(Long ownerProjectId, String slugOrAppCode, long seq) {
        String key = shortKeyFromSlug(slugOrAppCode);
        if (key.isBlank()) key = "APP";

        String projectKey = ownerProjectId == null
                ? "0"
                : Long.toString(ownerProjectId, 36).toUpperCase(Locale.ROOT);

        String yymm = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyMM"));

        return key + "-" + projectKey + "-" + yymm + "-" + seqBase36(seq, 5);
    }
    
    private void assignOrderCode(Order order, Long ownerProjectId, String slug) {
        Long seq = orderSeqRepo.allocateNext(ownerProjectId);
        if (seq == null) {
            throw new IllegalStateException("Failed to allocate order sequence");
        }

        String prefix = appPrefix(ownerProjectId, slug);
        String code = formatCode(ownerProjectId, prefix, seq);

        order.setOrderSeq(seq);
        order.setOrderCode(code);
    }
    /* ===============================
       QUOTE FROM CART (NO SIDE EFFECTS)
       =============================== */

    private BigDecimal computeItemsSubtotal(List<CartLine> lines) {
        BigDecimal sum = BigDecimal.ZERO;
        if (lines == null) return sum;

        for (CartLine l : lines) {
            if (l == null) continue;

            BigDecimal lineSubtotal = l.getLineSubtotal();
            if (lineSubtotal == null) {
                BigDecimal unit = (l.getUnitPrice() == null) ? BigDecimal.ZERO : l.getUnitPrice();
                int qty = l.getQuantity();
                lineSubtotal = unit.multiply(BigDecimal.valueOf(qty));
            }
            sum = sum.add(lineSubtotal);
        }
        return sum;
    }
    
  
    @Override
    public CheckoutSummaryResponse quoteCheckoutFromCart(Long userId, CheckoutRequest request) {

        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (request == null) throw new IllegalArgumentException("request is required");
        if (request.getCurrencyId() == null) throw new IllegalArgumentException("currencyId is required");

        Users user = usersRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        requireActiveUser(user);
        Cart cart = cartRepo.findByUser_IdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active cart"));

        if (cart.getItems() == null || cart.getItems().isEmpty())
            throw new IllegalStateException("Cart is empty");

        Map<Long, Integer> qtyByItemId = new LinkedHashMap<>();
        for (CartItem ci : cart.getItems()) {
            if (ci.getItem() == null || ci.getItem().getId() == null) continue;
            int qty = ci.getQuantity();
            if (qty <= 0) continue;
            qtyByItemId.merge(ci.getItem().getId(), qty, Integer::sum);
        }

        if (qtyByItemId.isEmpty())
            throw new IllegalStateException("Cart has no valid items");

        List<CartLine> lines = new ArrayList<>();
        Long ownerProjectId = null;
        boolean shippingRequired = false; // ✅ NEW
        List<String> reservedStatuses = List.of("PENDING", "COMPLETED", "CANCEL_REQUESTED");

        for (var e : qtyByItemId.entrySet()) {
            Long itemId = e.getKey();
            int qty = e.getValue();

            Item item = itemRepo.findById(itemId)
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
            assertItemPurchasable(item);

            if (isShippingRequired(item)) {
                shippingRequired = true;
            }

            if (item.getOwnerProject() == null || item.getOwnerProject().getId() == null)
                throw new IllegalStateException("Item " + item.getId() + " has no ownerProject");

            Long opId = item.getOwnerProject().getId();
            if (ownerProjectId == null) ownerProjectId = opId;
            else if (!ownerProjectId.equals(opId))
                throw new IllegalArgumentException("All cart items must belong to the same app");

            Integer stock = readStock(item);
            if (stock != null) {
                if (qty > stock) throw new IllegalStateException("Only " + stock + " left for itemId=" + itemId);
            } else {
                Integer cap = readSeatsCapacity(item);
                if (cap != null) {
                    int alreadyReserved = orderItemRepo.sumQuantityByItemIdAndStatusNames(item.getId(), reservedStatuses);
                    int remaining = cap - alreadyReserved;
                    if (qty > remaining) throw new IllegalStateException("Only " + remaining + " seats left for itemId=" + itemId);
                }
            }

            BigDecimal unit = resolveUnitPrice(item);

            CartLine line = new CartLine();
            String itemName = Optional.ofNullable(tryGet(item, "getName", "getItemName", "getTitle"))
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(x -> !x.isBlank())
                    .orElse(null);

            line.setItemName(itemName);
            line.setItemId(itemId);
            line.setQuantity(qty);
            line.setUnitPrice(unit);
            line.setLineSubtotal(unit.multiply(BigDecimal.valueOf(qty)));
            lines.add(line);
        }

        if (ownerProjectId == null)
            throw new IllegalStateException("Could not resolve ownerProjectId from cart");

        request.setLines(lines);

        // ✅ NEW: enforce shipping even on quote for physical items
        validateShippingOrThrow(request.getShippingAddress(), shippingRequired);

        return checkoutPricingService.priceCheckout(
                ownerProjectId,
                request.getCurrencyId(),
                request
        );
    }
    /* ===============================
       CHECKOUT (CREATE ORDER + PAYMENT)
       =============================== */

    @Override
    public CheckoutSummaryResponse checkout(Long userId, CheckoutRequest request) {

        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (request == null || request.getLines() == null || request.getLines().isEmpty())
            throw new IllegalArgumentException("Cart lines are required");
        if (request.getCurrencyId() == null)
            throw new IllegalArgumentException("currencyId is required");
        if (request.getPaymentMethod() == null || request.getPaymentMethod().isBlank())
            throw new IllegalArgumentException("paymentMethod is required");

        String paymentMethodCode = request.getPaymentMethod().trim().toUpperCase(Locale.ROOT);

        Users user = usersRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        requireActiveUser(user);

        Currency currency = currencyRepo.findById(request.getCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException("Currency not found"));

        List<CartLine> lines = request.getLines();
        Map<Long, Item> itemCache = new HashMap<>();
        Long ownerProjectId = null;
        String ownerSlug = null;

        boolean shippingRequired = false; // ✅ NEW

        for (CartLine line : lines) {
            if (line.getItemId() == null)
                throw new IllegalArgumentException("itemId is required in cart line");
            if (line.getQuantity() <= 0)
                throw new IllegalArgumentException("quantity must be > 0 for itemId = " + line.getItemId());

            Item item = itemRepo.findById(line.getItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + line.getItemId()));
            assertItemPurchasable(item);
            itemCache.put(item.getId(), item);

            if (isShippingRequired(item)) shippingRequired = true; // ✅ NEW

            Object n = tryGet(item, "getName", "getItemName", "getTitle");
            String itemName = (n == null) ? null : n.toString().trim();
            if (itemName != null && !itemName.isBlank()) {
                line.setItemName(itemName);
            }

            if (item.getOwnerProject() == null || item.getOwnerProject().getId() == null)
                throw new IllegalStateException("Item " + item.getId() + " has no ownerProject");

            Long opId = item.getOwnerProject().getId();
            if (ownerProjectId == null) {
                ownerProjectId = opId;
                ownerSlug = item.getOwnerProject().getSlug();
            }
            else if (!ownerProjectId.equals(opId))
                throw new IllegalArgumentException("All cart items must belong to the same app (ownerProjectId)");

            Integer capacity = tryCapacity(item);
            if (capacity != null) {
                int already = orderItemRepo.sumQuantityByItemIdAndStatusNames(item.getId(), List.of("COMPLETED"));
                int remaining = capacity - already;
                if (line.getQuantity() > remaining)
                    throw new IllegalStateException("Not enough quantity available for itemId = " + item.getId());
            }

            BigDecimal unit = resolveUnitPrice(item);
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(line.getQuantity()));
            line.setUnitPrice(unit);
            line.setLineSubtotal(lineTotal);
        }

        if (ownerProjectId == null)
            throw new IllegalArgumentException("Could not resolve ownerProjectId from cart items");

        // ✅ NEW: enforce shipping here too (blocks direct /checkout calls)
        validateShippingOrThrow(request.getShippingAddress(), shippingRequired);

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

        String shippingName = (addr != null && addr.getFullName() != null) ? addr.getFullName().trim() : "";
        if (shippingName.isBlank()) {
            String fn = user.getFirstName() == null ? "" : user.getFirstName().trim();
            String ln = user.getLastName() == null ? "" : user.getLastName().trim();
            String full = (fn + " " + ln).trim();
            shippingName = full.isBlank() ? String.valueOf(user.getUsername()) : full;
        }

        CheckoutSummaryResponse priced;
        String couponMessage = null;

        try {
            priced = checkoutPricingService.priceCheckout(
                    ownerProjectId,
                    request.getCurrencyId(),
                    request
            );
        } catch (Exception ex) {
            if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
                request.setCouponCode(null);

                priced = checkoutPricingService.priceCheckout(
                        ownerProjectId,
                        request.getCurrencyId(),
                        request
                );

                couponMessage = mapCouponErrorMessage(ex);
            } else {
                throw ex;
            }
        }

     // ✅ FIX: Coupon must NOT be consumed or stored unless it actually applied
        boolean couponApplied = false;
        

        String pricedCouponCode = priced.getCouponCode();
        BigDecimal pricedCouponDiscount = (priced.getCouponDiscount() == null)
                ? BigDecimal.ZERO
                : priced.getCouponDiscount();

        if (pricedCouponCode != null && !pricedCouponCode.isBlank()) {
            try {
                BigDecimal itemsSubtotal = computeItemsSubtotal(lines);

                // Re-validate against actual order subtotal (minOrderAmount etc)
                Coupon validated = couponService.validateForOrder(ownerProjectId, pricedCouponCode, itemsSubtotal);

                if (validated == null) {
                    priced.setCouponCode(null);
                    priced.setCouponDiscount(BigDecimal.ZERO);
                    couponMessage = "Coupon was not applied";
                } else {
                    // FREE_SHIPPING counts as applied even if discount = 0
                	if (validated.getType() == CouponDiscountType.FREE_SHIPPING){
                        couponApplied = true;
                        priced.setCouponCode(validated.getCode());
                    } else {
                        // FIXED/PERCENT must produce real discount
                        if (pricedCouponDiscount.compareTo(BigDecimal.ZERO) > 0) {
                            couponApplied = true;
                            priced.setCouponCode(validated.getCode());
                        } else {
                            priced.setCouponCode(null);
                            priced.setCouponDiscount(BigDecimal.ZERO);
                            couponMessage = "Coupon was not applied because it did not affect this order";
                        }
                    }
                }
            } catch (Exception ex) {
                priced.setCouponCode(null);
                priced.setCouponDiscount(BigDecimal.ZERO);
                couponMessage = mapCouponErrorMessage(ex);
            }
        }

        PaymentMethod pmEntity = paymentMethodRepo.findByNameIgnoreCase(paymentMethodCode)
                .orElseThrow(() -> new IllegalArgumentException("Payment method not found in platform: " + paymentMethodCode));

        Order order = new Order();
        order.setUser(user);
        order.setStatus(requireStatus("PENDING"));
        order.setOrderDate(LocalDateTime.now()); // creation time
        order.setCurrency(currency);
        order.setTotalPrice(priced.getGrandTotal());
        order.setPaymentMethod(pmEntity);

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

        order.setShippingTotal(priced.getShippingTotal());
        order.setItemTaxTotal(priced.getItemTaxTotal());
        order.setShippingTaxTotal(priced.getShippingTaxTotal());
        order.setCouponCode(priced.getCouponCode());
        order.setCouponDiscount(priced.getCouponDiscount());

        assignOrderCode(order, ownerProjectId, ownerSlug);
        order = orderRepo.save(order);

        for (CartLine line : lines) {
            Item item = itemCache.get(line.getItemId());
            if (item == null) {
                item = itemRepo.findById(line.getItemId())
                        .orElseThrow(() -> new IllegalArgumentException("Item not found: " + line.getItemId()));
            }

            OrderItem oi = new OrderItem();
            oi.setOrder(order);
            oi.setItem(item);
            oi.setUser(user);
            oi.setCurrency(currency);
            oi.setQuantity(line.getQuantity());
            oi.setPrice(line.getUnitPrice());
            oi.setCreatedAt(LocalDateTime.now());
            oi.setUpdatedAt(LocalDateTime.now());
            orderItemRepo.save(oi);
        }

        if (couponApplied) {
            try {
                couponService.consumeOrThrow(ownerProjectId, priced.getCouponCode());
            } catch (Exception ex) {
                BigDecimal restoredGrandTotal = priced.getItemsSubtotal()
                        .add(priced.getShippingTotal() == null ? BigDecimal.ZERO : priced.getShippingTotal())
                        .add(priced.getItemTaxTotal() == null ? BigDecimal.ZERO : priced.getItemTaxTotal())
                        .add(priced.getShippingTaxTotal() == null ? BigDecimal.ZERO : priced.getShippingTaxTotal());

                priced.setCouponCode(null);
                priced.setCouponDiscount(BigDecimal.ZERO);
                priced.setGrandTotal(restoredGrandTotal);

                order.setCouponCode(null);
                order.setCouponDiscount(BigDecimal.ZERO);
                order.setTotalPrice(restoredGrandTotal);
                orderRepo.save(order);

                couponMessage = mapCouponErrorMessage(ex);
                couponApplied = false;
            }
        }

        StartPaymentResponse pay = paymentOrchestrator.startPayment(
                ownerProjectId,
                order.getId(),
                paymentMethodCode,
                priced.getGrandTotal(),
                currency.getCode(),
                request.getDestinationAccountId()
        );
        
        
        priced.setPaymentTransactionId(pay.getTransactionId());
        priced.setPaymentProviderCode(pay.getProviderCode());
        priced.setProviderPaymentId(pay.getProviderPaymentId());
        priced.setClientSecret(pay.getClientSecret());
        priced.setPublishableKey(pay.getPublishableKey());
        priced.setRedirectUrl(pay.getRedirectUrl());
        priced.setPaymentStatus(pay.getStatus());

        priced.setOrderId(order.getId());
        priced.setOrderDate(order.getOrderDate());
        priced.setOrderCode(order.getOrderCode());
        priced.setOrderSeq(order.getOrderSeq());

        Cart cart = cartRepo.findByUser_IdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active cart"));

        cart.setStatus(CartStatus.CONVERTED);

        cartItemRepo.deleteByCart_Id(cart.getId());
        cart.getItems().clear();
        recalcTotals(cart);
        cartRepo.save(cart);

        try {
            Long ownerUserId = resolveOwnerUserId(ownerProjectId);

            frontAppNotificationService.notifyOwnerOrderCreated(
                    ownerProjectId,
                    ownerUserId,
                    userId,
                    order.getId(),
                    order.getOrderCode()
            );
        } catch (Exception e) {
            System.out.println("Order created successfully, but failed to send owner notification => " + e.getMessage());
        }

        if (couponMessage != null && !couponMessage.isBlank()) {
            priced.setMessage(couponMessage);
        }
        return priced;
    }

    public CheckoutSummaryResponse checkoutFromCart(Long userId, CheckoutRequest request) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (request == null) throw new IllegalArgumentException("request is required");
        if (request.getCurrencyId() == null) throw new IllegalArgumentException("currencyId is required");
        if (request.getPaymentMethod() == null || request.getPaymentMethod().isBlank())
            throw new IllegalArgumentException("paymentMethod is required");

        Users user = usersRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        requireActiveUser(user);

        Cart cart = cartRepo.findByUser_IdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active cart"));

        if (cart.getItems() == null || cart.getItems().isEmpty())
            throw new IllegalStateException("Cart is empty");

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

        List<CartLine> lines = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : qtyByItemId.entrySet()) {
            CartLine line = new CartLine();
            line.setItemId(e.getKey());
            line.setQuantity(e.getValue());
            lines.add(line);
        }

        List<String> blockingErrors = new ArrayList<>();
        List<Map<String, Object>> lineErrors = new ArrayList<>();

        Long ownerProjectId = null;
        boolean shippingRequired = false;
        List<String> reservedStatuses = List.of("PENDING", "COMPLETED", "CANCEL_REQUESTED");

        for (CartLine line : lines) {
            if (line.getItemId() == null) continue;

            Item fresh = itemRepo.findByIdForStockCheck(line.getItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + line.getItemId()));
            assertItemPurchasable(fresh);

            if (isShippingRequired(fresh)) {
                shippingRequired = true;
            }

            if (fresh.getOwnerProject() == null || fresh.getOwnerProject().getId() == null) {
                blockingErrors.add("Item " + fresh.getId() + " has no ownerProject");
                lineErrors.add(Map.of("itemId", fresh.getId(), "reason", "NO_TENANT", "message", "Item has no ownerProject"));
                continue;
            }

            Long opId = fresh.getOwnerProject().getId();
            if (ownerProjectId == null) ownerProjectId = opId;
            else if (!ownerProjectId.equals(opId)) {
                blockingErrors.add("Cart contains items from multiple apps (tenant mix)");
                lineErrors.add(Map.of("itemId", fresh.getId(), "reason", "TENANT_MISMATCH", "message", "Item belongs to a different app"));
                continue;
            }

            int qty = line.getQuantity();
            if (qty <= 0) {
                blockingErrors.add("Invalid quantity for item " + fresh.getId());
                lineErrors.add(Map.of("itemId", fresh.getId(), "reason", "INVALID_QTY", "message", "Quantity must be > 0"));
                continue;
            }

            Integer stock = readStock(fresh);
            if (stock != null) {
                if (stock <= 0 || qty > stock) {
                    blockingErrors.add("Only " + stock + " left for item " + fresh.getId());
                    lineErrors.add(Map.of(
                            "itemId", fresh.getId(),
                            "reason", "QTY_EXCEEDS_STOCK",
                            "availableStock", stock,
                            "maxAllowedQuantity", Math.max(stock, 0)
                    ));
                }
                continue;
            }

            Integer capacity = readSeatsCapacity(fresh);
            if (capacity != null) {
                int alreadyReserved = orderItemRepo.sumQuantityByItemIdAndStatusNames(fresh.getId(), reservedStatuses);
                int remaining = capacity - alreadyReserved;
                if (remaining <= 0 || qty > remaining) {
                    blockingErrors.add("Only " + remaining + " seats left for item " + fresh.getId());
                    lineErrors.add(Map.of(
                            "itemId", fresh.getId(),
                            "reason", "QTY_EXCEEDS_CAPACITY",
                            "availableStock", Math.max(remaining, 0),
                            "maxAllowedQuantity", Math.max(remaining, 0)
                    ));
                }
            }
        }

        if (!blockingErrors.isEmpty()) {
            throw new com.build4all.order.web.CheckoutBlockedException(blockingErrors, lineErrors);
        }

        // ✅ validate shipping BEFORE decrementing stock
        validateShippingOrThrow(request.getShippingAddress(), shippingRequired);

        for (CartLine line : lines) {
            if (line.getItemId() == null) continue;

            Item fresh = itemRepo.findByIdForStockCheck(line.getItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + line.getItemId()));
            assertItemPurchasable(fresh);

            Integer stock = readStock(fresh);
            if (stock != null) {
                int qty = line.getQuantity();
                int updated = itemRepo.decrementStockIfEnough(fresh.getId(), qty);
                if (updated != 1) {
                    throw new com.build4all.order.web.CheckoutBlockedException(
                            List.of("Stock changed for item " + fresh.getId() + ". Please refresh cart."),
                            List.of(Map.of("itemId", fresh.getId(), "reason", "STOCK_CHANGED", "message", "Stock changed, please refresh"))
                    );
                }

                Integer newStock = itemRepo.findStockValue(fresh.getId());
                Long tenantId = fresh.getOwnerProject().getId();
                wsEvents.sendStockChanged(tenantId, fresh.getId(), -qty, newStock, "ORDER_RESERVED", null);

                notifyOwnerStockAlertIfNeeded(fresh, newStock);
            }
        }

        request.setLines(lines);
        return checkout(userId, request);
    }

    /* -------------------- helpers -------------------- */

    private Integer readStock(Item item) {
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
               Integer newStock = itemRepo.findStockValue(item.getId());
               Long tenantId = item.getOwnerProject().getId();
               wsEvents.sendStockChanged(tenantId, item.getId(), +qty, newStock, "ORDER_RELEASED", order.getId());
            }
        }
    }
    
    
    private void reserveStockForOrder(Order order) {
        if (order == null || order.getOrderItems() == null) return;

        for (OrderItem oi : order.getOrderItems()) {
            Item item = oi.getItem();
            if (item == null || item.getId() == null) continue;

            Integer stock = readStock(item);
            if (stock == null) continue; // non-stock item

            int qty = oi.getQuantity();
            if (qty <= 0) continue;

            int updated = itemRepo.decrementStockIfEnough(item.getId(), qty);
            if (updated != 1) {
                Integer currentStock = itemRepo.findStockValue(item.getId());
                int available = currentStock == null ? 0 : currentStock;
                throw new IllegalStateException(
                        "Not enough stock to restore order for itemId=" + item.getId() + ". Available: " + available
                );
            }

            Integer newStock = itemRepo.findStockValue(item.getId());
            Long tenantId = item.getOwnerProject() != null ? item.getOwnerProject().getId() : null;
            if (tenantId != null) {
                wsEvents.sendStockChanged(tenantId, item.getId(), -qty, newStock, "ORDER_RESTORED", order.getId());
            }
        }
    }

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
    
    private void assertOwnerOwnsOrder(Order order, Long ownerProjectId) {
        boolean ok = order.getOrderItems() != null && order.getOrderItems().stream().anyMatch(oi ->
                oi != null
                        && oi.getItem() != null
                        && oi.getItem().getOwnerProject() != null
                        && ownerProjectId.equals(oi.getItem().getOwnerProject().getId())
        );

        if (!ok) {
            throw new NoSuchElementException("Order not found");
        }
    }
    
    private void clearShippingFields(Order order) {
        order.setShippingCountry(null);
        order.setShippingRegion(null);
        order.setShippingCity(null);
        order.setShippingPostalCode(null);
        order.setShippingMethodId(null);
        order.setShippingMethodName(null);
        order.setShippingAddress(null);
        order.setShippingPhone(null);
        order.setShippingFullName(null);
    }
    
    private void applyShippingToOrder(Order order, ShippingAddressDTO addr, boolean shippingRequired) {
        if (!shippingRequired) {
            clearShippingFields(order);
            return;
        }

        if (addr == null) {
            throw new IllegalArgumentException("CHECKOUT_SHIPPING_ADDRESS_REQUIRED");
        }

        Country shippingCountry = null;
        Region shippingRegion = null;

        if (addr.getCountryId() != null) {
            shippingCountry = countryRepo.findById(addr.getCountryId())
                    .orElseThrow(() -> new IllegalArgumentException("Shipping country not found"));
        }

        if (addr.getRegionId() != null) {
            shippingRegion = regionRepo.findById(addr.getRegionId())
                    .orElseThrow(() -> new IllegalArgumentException("Shipping region not found"));
        }

        Users user = order.getUser();
        String shippingName = trimOrNull(addr.getFullName());

        if (shippingName == null && user != null) {
            String fn = user.getFirstName() == null ? "" : user.getFirstName().trim();
            String ln = user.getLastName() == null ? "" : user.getLastName().trim();
            String full = (fn + " " + ln).trim();
            shippingName = full.isBlank() ? String.valueOf(user.getUsername()) : full;
        }

        order.setShippingCountry(shippingCountry);
        order.setShippingRegion(shippingRegion);
        order.setShippingCity(trimOrNull(addr.getCity()));
        order.setShippingPostalCode(trimOrNull(addr.getPostalCode()));
        order.setShippingMethodId(addr.getShippingMethodId());
        order.setShippingMethodName(trimOrNull(addr.getShippingMethodName()));
        order.setShippingAddress(trimOrNull(addr.getAddressLine()));
        order.setShippingPhone(trimOrNull(addr.getPhone()));
        order.setShippingFullName(shippingName);
    }
    
    private CheckoutRequest buildEditPricingRequest(
            Order order,
            OrderEditRequest request,
            Map<Long, Integer> newQty,
            Map<Long, OrderItem> existingByItemId,
            Map<Long, Item> lockedItems
    ) {
        CheckoutRequest pricingRequest = new CheckoutRequest();
        pricingRequest.setCurrencyId(order.getCurrency().getId());
        pricingRequest.setShippingAddress(request.getShippingAddress());
        pricingRequest.setCouponCode(trimOrNull(order.getCouponCode()));

        List<CartLine> lines = new ArrayList<>();

        for (Map.Entry<Long, Integer> e : newQty.entrySet()) {
            Long itemId = e.getKey();
            int qty = e.getValue();

            Item item = lockedItems.get(itemId);
            OrderItem existing = existingByItemId.get(itemId);

            BigDecimal unitPrice =
                    (existing != null && existing.getPrice() != null)
                            ? existing.getPrice()
                            : resolveUnitPrice(item);

            CartLine cl = new CartLine();
            cl.setItemId(itemId);
            cl.setQuantity(qty);
            cl.setUnitPrice(unitPrice);
            cl.setLineSubtotal(unitPrice.multiply(BigDecimal.valueOf(qty)));

            Object n = tryGet(item, "getName", "getItemName", "getTitle");
            if (n != null) {
                String itemName = n.toString().trim();
                if (!itemName.isBlank()) cl.setItemName(itemName);
            }

            lines.add(cl);
        }

        pricingRequest.setLines(lines);
        return pricingRequest;
    }
    
    
    @Override
    public Map<String, Object> ownerEditOrder(Long orderId, Long ownerProjectId, OrderEditRequest request) {

        if (orderId == null) throw new IllegalArgumentException("orderId is required");
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId is required");
        if (request == null || request.getLines() == null || request.getLines().isEmpty()) {
            throw new IllegalArgumentException("Edited order lines are required");
        }

        Order order = orderRepo.findByIdWithItems(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        assertOwnerOwnsOrder(order, ownerProjectId);

        String curr = currentStatusCode(order);
        if (!List.of("PENDING", "CANCEL_REQUESTED").contains(curr)) {
            throw new IllegalStateException("Only pending orders can be edited");
        }

        String provider = providerForOrder(order);

        // V1 safe rule
        if (!"CASH".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("Only CASH orders can be edited for now");
        }

        if (order.getCurrency() == null || order.getCurrency().getId() == null) {
            throw new IllegalStateException("Order currency is missing");
        }

        Map<Long, OrderItem> existingByItemId = new LinkedHashMap<>();
        Map<Long, Integer> oldQty = new LinkedHashMap<>();

        if (order.getOrderItems() != null) {
            for (OrderItem oi : order.getOrderItems()) {
                if (oi == null || oi.getItem() == null || oi.getItem().getId() == null) continue;

                Long itemId = oi.getItem().getId();
                existingByItemId.putIfAbsent(itemId, oi);
                oldQty.merge(itemId, oi.getQuantity(), Integer::sum);
            }
        }

        Map<Long, Integer> newQty = new LinkedHashMap<>();
        for (var l : request.getLines()) {
            if (l == null || l.getItemId() == null) {
                throw new IllegalArgumentException("itemId is required");
            }
            if (l.getQuantity() < 0) {
                throw new IllegalArgumentException("quantity cannot be negative");
            }
            if (l.getQuantity() == 0) continue; // remove
            newQty.merge(l.getItemId(), l.getQuantity(), Integer::sum);
        }

        if (newQty.isEmpty()) {
            throw new IllegalArgumentException("Edited order must contain at least one item");
        }

        Set<Long> affectedIds = new TreeSet<>();
        affectedIds.addAll(oldQty.keySet());
        affectedIds.addAll(newQty.keySet());

        Map<Long, Item> lockedItems = new LinkedHashMap<>();
        boolean shippingRequired = false;

        for (Long itemId : affectedIds) {
            Item locked = itemRepo.findByIdForStockCheck(itemId)
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));

            int oldQuantity = oldQty.getOrDefault(itemId, 0);
            int newQuantity = newQty.getOrDefault(itemId, 0);

            boolean isExistingLine = oldQuantity > 0;
            boolean isNewLine = oldQuantity == 0 && newQuantity > 0;
            boolean isIncreasingExisting = isExistingLine && newQuantity > oldQuantity;

            // ✅ business rule:
            // - new item must be purchasable
            // - increasing an existing item must also be purchasable
            // - keeping same qty / reducing qty / removing item is allowed
            //   even if the item is no longer published
            if (isNewLine || isIncreasingExisting) {
                assertItemPurchasable(locked);
            }

            if (locked.getOwnerProject() == null || locked.getOwnerProject().getId() == null) {
                throw new IllegalStateException("Item " + itemId + " has no ownerProject");
            }

            if (!ownerProjectId.equals(locked.getOwnerProject().getId())) {
                throw new IllegalArgumentException("All edited items must belong to the same owner project");
            }

            lockedItems.put(itemId, locked);

            if (newQuantity > 0 && isShippingRequired(locked)) {
                shippingRequired = true;
            }
        }

        validateShippingOrThrow(request.getShippingAddress(), shippingRequired);

        List<String> reservedStatuses = List.of("PENDING", "COMPLETED", "CANCEL_REQUESTED");

        // capacity validation
        for (Long itemId : affectedIds) {
            Item item = lockedItems.get(itemId);
            if (item == null) continue;

            int oldQuantity = oldQty.getOrDefault(itemId, 0);
            int newQuantity = newQty.getOrDefault(itemId, 0);

            Integer stock = readStock(item);
            if (stock != null) continue;

            Integer capacity = readSeatsCapacity(item);
            if (capacity != null) {
                int totalReserved = orderItemRepo.sumQuantityByItemIdAndStatusNames(itemId, reservedStatuses);
                int reservedByOthers = Math.max(0, totalReserved - oldQuantity);
                int remainingForThisOrder = capacity - reservedByOthers;

                if (newQuantity > remainingForThisOrder) {
                    throw new IllegalStateException(
                            "Only " + Math.max(remainingForThisOrder, 0) + " available for itemId=" + itemId
                    );
                }
            }
        }

        // stock delta
        for (Long itemId : affectedIds) {
            Item item = lockedItems.get(itemId);
            if (item == null) continue;

            Integer stock = readStock(item);
            if (stock == null) continue;

            int oldQuantity = oldQty.getOrDefault(itemId, 0);
            int newQuantity = newQty.getOrDefault(itemId, 0);
            int delta = newQuantity - oldQuantity;

            if (delta > 0) {
                int updated = itemRepo.decrementStockIfEnough(itemId, delta);
                if (updated != 1) {
                    Integer currentStock = itemRepo.findStockValue(itemId);
                    int available = currentStock == null ? 0 : currentStock;
                    throw new IllegalStateException(
                            "Not enough stock for itemId=" + itemId + ". Available: " + available
                    );
                }

                Integer newStock = itemRepo.findStockValue(itemId);
                Long tenantId = item.getOwnerProject() != null ? item.getOwnerProject().getId() : null;
                if (tenantId != null) {
                    wsEvents.sendStockChanged(tenantId, itemId, -delta, newStock, "ORDER_EDITED", order.getId());
                }

                notifyOwnerStockAlertIfNeeded(item, newStock);

            } else if (delta < 0) {
                int returnedQty = -delta;
                itemRepo.incrementStock(itemId, returnedQty);

                Integer newStock = itemRepo.findStockValue(itemId);
                Long tenantId = item.getOwnerProject() != null ? item.getOwnerProject().getId() : null;
                if (tenantId != null) {
                    wsEvents.sendStockChanged(tenantId, itemId, returnedQty, newStock, "ORDER_EDITED", order.getId());
                }
            }
        }

        // delete removed lines
        if (order.getOrderItems() != null) {
            Iterator<OrderItem> it = order.getOrderItems().iterator();
            while (it.hasNext()) {
                OrderItem oi = it.next();
                if (oi == null || oi.getItem() == null || oi.getItem().getId() == null) continue;

                Long itemId = oi.getItem().getId();
                if (!newQty.containsKey(itemId)) {
                    orderItemRepo.delete(oi);
                    it.remove();
                }
            }
        }

        LocalDateTime now = LocalDateTime.now();

        // update/add lines
        for (Map.Entry<Long, Integer> e : newQty.entrySet()) {
            Long itemId = e.getKey();
            int qty = e.getValue();

            Item item = lockedItems.get(itemId);
            OrderItem existing = existingByItemId.get(itemId);

            if (existing == null) {
                OrderItem oi = new OrderItem();
                oi.setOrder(order);
                oi.setItem(item);
                oi.setUser(order.getUser());
                oi.setCurrency(order.getCurrency());
                oi.setQuantity(qty);
                oi.setPrice(resolveUnitPrice(item));
                oi.setCreatedAt(now);
                oi.setUpdatedAt(now);
                orderItemRepo.save(oi);

                if (order.getOrderItems() != null) {
                    order.getOrderItems().add(oi);
                }
            } else {
                existing.setQuantity(qty);
                if (existing.getPrice() == null) {
                    existing.setPrice(resolveUnitPrice(item));
                }
                existing.setUpdatedAt(now);
                orderItemRepo.save(existing);
            }
        }

        CheckoutRequest pricingRequest = buildEditPricingRequest(
                order,
                request,
                newQty,
                existingByItemId,
                lockedItems
        );

        CheckoutSummaryResponse priced;
        String editMessage = null;

        String previousCouponCode = trimOrNull(order.getCouponCode());
        boolean releaseRemovedCoupon = false;

        try {
            priced = checkoutPricingService.priceCheckout(
                    ownerProjectId,
                    order.getCurrency().getId(),
                    pricingRequest
            );
        } catch (Exception ex) {
            if (previousCouponCode != null) {
                pricingRequest.setCouponCode(null);

                priced = checkoutPricingService.priceCheckout(
                        ownerProjectId,
                        order.getCurrency().getId(),
                        pricingRequest
                );

                editMessage = mapCouponErrorMessage(ex);
                releaseRemovedCoupon = true;
            } else {
                throw ex;
            }
        }

        if (releaseRemovedCoupon && previousCouponCode != null) {
            couponService.releaseOne(ownerProjectId, previousCouponCode);
        }

        applyShippingToOrder(order, request.getShippingAddress(), shippingRequired);

        order.setShippingTotal(priced.getShippingTotal());
        order.setItemTaxTotal(priced.getItemTaxTotal());
        order.setShippingTaxTotal(priced.getShippingTaxTotal());
        order.setCouponCode(priced.getCouponCode());
        order.setCouponDiscount(priced.getCouponDiscount());
        order.setTotalPrice(priced.getGrandTotal());

        orderRepo.save(order);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message",
                (editMessage == null || editMessage.isBlank())
                        ? "Order updated successfully"
                        : "Order updated successfully. " + editMessage
        );
        res.put("orderId", order.getId());
        res.put("orderCode", order.getOrderCode());
        res.put("status", currentStatusCode(order));
        res.put("totalPrice", order.getTotalPrice());
        res.put("shippingTotal", order.getShippingTotal());
        res.put("itemTaxTotal", order.getItemTaxTotal());
        res.put("shippingTaxTotal", order.getShippingTaxTotal());
        res.put("couponCode", order.getCouponCode());
        res.put("couponDiscount", order.getCouponDiscount());

        return res;
    }

    @Override
    public void failCashOrder(Long orderItemId, Long businessId, String reason) {

        OrderItem oi = requireBusinessOwned(orderItemId, businessId);
        Order order = oi.getOrder();
        if (order == null) throw new IllegalStateException("Missing order");

        String curr = currentStatusCode(order);
        if ("COMPLETED".equals(curr)) throw new IllegalStateException("Cannot fail a completed order");
        if ("CANCELED".equals(curr) || "REJECTED".equals(curr) || "EXPIRED".equals(curr)) return;

        var ps = paymentRead.summaryForOrder(order.getId(), order.getTotalPrice());
        if (!ps.isFullyPaid()) {
            Order full = orderRepo.findByIdWithItems(order.getId())
                    .orElseThrow(() -> new IllegalStateException("Order not found"));

            releaseStockForOrder(full);
            releaseCouponForOrderIfAny(full);

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