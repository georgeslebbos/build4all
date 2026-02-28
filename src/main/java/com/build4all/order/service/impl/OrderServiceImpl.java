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
import com.build4all.order.domain.OrderSequence;
import com.build4all.order.domain.OrderStatus;
import com.build4all.order.dto.CartLine;
import com.build4all.order.dto.CheckoutRequest;
import com.build4all.order.dto.CheckoutSummaryResponse;
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
import com.build4all.promo.service.CouponService;
import com.build4all.user.domain.Users;
import com.build4all.user.repository.UsersRepository;
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
            OrderSequenceRepository orderSeqRepo
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
    }

    /* ===============================
       STATUS HELPERS
       =============================== */

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

        Item item = itemRepo.findByIdForStockCheck(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));

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
        Item item = itemRepo.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

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

        if ("COMPLETED".equals(curr)) throw new IllegalStateException("Cannot reset a completed order");
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
    }

    @Override
    public void rejectCancel(Long orderItemId, Long businessId) {
        var oi = requireBusinessOwned(orderItemId, businessId);
        String curr = currentStatusCode(oi.getOrder());

        if ("CANCELED".equals(curr)) throw new IllegalStateException("Already canceled");
        if ("COMPLETED".equals(curr)) return;

        flipStatus(oi, "PENDING");
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

        // ✅ FIX: do NOT touch orderDate (created time)
        // order.setOrderDate(LocalDateTime.now());

        orderRepo.save(order);
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

        order.setStatus(requireStatus("COMPLETED"));

        // ✅ FIX: do NOT touch orderDate
        // order.setOrderDate(LocalDateTime.now());

        orderRepo.save(order);
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
    }

    @Override
    public void unrejectorder(Long orderItemId, Long businessId) {
        var oi = requireBusinessOwned(orderItemId, businessId);
        String curr = currentStatusCode(oi.getOrder());

        if ("COMPLETED".equals(curr)) throw new IllegalStateException("Cannot restore completed order");
        flipStatus(oi, "PENDING");
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

    private String formatCode(String slugOrAppCode, long seq) {
        String key = shortKeyFromSlug(slugOrAppCode);
        if (key.isBlank()) key = "APP";
        String yymm = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyMM"));
        return key + "-" + yymm + "-" + seqBase36(seq, 5);
    }
    
    private synchronized void assignOrderCode(Order order, Long ownerProjectId, String slug) {
        OrderSequence seq = orderSeqRepo.findForUpdate(ownerProjectId)
                .orElseGet(() -> {
                    OrderSequence s = new OrderSequence();
                    s.setOwnerProjectId(ownerProjectId);
                    s.setNextSeq(1L);
                    return s;
                });

        long current = seq.getNextSeq();
        seq.setNextSeq(current + 1);
        orderSeqRepo.save(seq);

        String prefix = appPrefix(ownerProjectId, slug);
        String code = formatCode(prefix, current);

        order.setOrderSeq(current);
        order.setOrderCode(code);
    }
    
    /* ===============================
       QUOTE FROM CART (NO SIDE EFFECTS)
       =============================== */

    @Override
    public CheckoutSummaryResponse quoteCheckoutFromCart(Long userId, CheckoutRequest request) {

        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (request == null) throw new IllegalArgumentException("request is required");
        if (request.getCurrencyId() == null) throw new IllegalArgumentException("currencyId is required");

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
        List<String> reservedStatuses = List.of("PENDING", "COMPLETED", "CANCEL_REQUESTED");

        for (var e : qtyByItemId.entrySet()) {
            Long itemId = e.getKey();
            int qty = e.getValue();

            Item item = itemRepo.findById(itemId)
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
            
            

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

        Currency currency = currencyRepo.findById(request.getCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException("Currency not found"));

        List<CartLine> lines = request.getLines();
        Map<Long, Item> itemCache = new HashMap<>();
        Long ownerProjectId = null;
        String ownerSlug = null;

        for (CartLine line : lines) {
            if (line.getItemId() == null)
                throw new IllegalArgumentException("itemId is required in cart line");
            if (line.getQuantity() <= 0)
                throw new IllegalArgumentException("quantity must be > 0 for itemId = " + line.getItemId());

            Item item = itemRepo.findById(line.getItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + line.getItemId()));
            itemCache.put(item.getId(), item);
            
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
                 // if ownerProject is AdminUserProject (AUP), this exists
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

        CheckoutSummaryResponse priced = checkoutPricingService.priceCheckout(
                ownerProjectId,
                request.getCurrencyId(),
                request
        );

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

        StartPaymentResponse pay = paymentOrchestrator.startPayment(
                ownerProjectId,
                order.getId(),
                paymentMethodCode,
                priced.getGrandTotal(),
                currency.getCode(),
                request.getDestinationAccountId()
        );

        // consume coupon ONLY after payment start succeeded
        String couponCode = priced.getCouponCode();
        if (couponCode != null && !couponCode.isBlank()) {
            couponService.consumeOrThrow(ownerProjectId, couponCode);
        }

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

        return priced;
    }

    public CheckoutSummaryResponse checkoutFromCart(Long userId, CheckoutRequest request) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (request == null) throw new IllegalArgumentException("request is required");
        if (request.getCurrencyId() == null) throw new IllegalArgumentException("currencyId is required");
        if (request.getPaymentMethod() == null || request.getPaymentMethod().isBlank())
            throw new IllegalArgumentException("paymentMethod is required");

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
        List<String> reservedStatuses = List.of("PENDING", "COMPLETED", "CANCEL_REQUESTED");

        for (CartLine line : lines) {
            if (line.getItemId() == null) continue;

            Item fresh = itemRepo.findByIdForStockCheck(line.getItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + line.getItemId()));

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
                    lineErrors.add(Map.of("itemId", fresh.getId(), "reason", "QTY_EXCEEDS_STOCK", "availableStock", stock, "maxAllowedQuantity", Math.max(stock, 0)));
                }
                continue;
            }

            Integer capacity = readSeatsCapacity(fresh);
            if (capacity != null) {
                int alreadyReserved = orderItemRepo.sumQuantityByItemIdAndStatusNames(fresh.getId(), reservedStatuses);
                int remaining = capacity - alreadyReserved;
                if (remaining <= 0 || qty > remaining) {
                    blockingErrors.add("Only " + remaining + " seats left for item " + fresh.getId());
                    lineErrors.add(Map.of("itemId", fresh.getId(), "reason", "QTY_EXCEEDS_CAPACITY", "availableStock", Math.max(remaining, 0), "maxAllowedQuantity", Math.max(remaining, 0)));
                }
            }
        }

        if (!blockingErrors.isEmpty()) {
            throw new com.build4all.order.web.CheckoutBlockedException(blockingErrors, lineErrors);
        }

        for (CartLine line : lines) {
            if (line.getItemId() == null) continue;

            Item fresh = itemRepo.findByIdForStockCheck(line.getItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + line.getItemId()));

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
            if (qty > 0) itemRepo.incrementStock(item.getId(), qty);
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