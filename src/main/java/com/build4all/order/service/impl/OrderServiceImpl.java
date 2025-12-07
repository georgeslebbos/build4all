package com.build4all.order.service.impl;

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
    private final CountryRepository countryRepo;
    private final RegionRepository regionRepo;

    // 🔹 NEW: central pricing engine (shipping + tax + coupon)
    private final CheckoutPricingService checkoutPricingService;

    public OrderServiceImpl(OrderItemRepository orderItemRepo,
                            OrderRepository orderRepo,
                            UsersRepository usersRepo,
                            ItemRepository itemRepo,
                            CurrencyRepository currencyRepo,
                            OrderStatusRepository orderStatusRepo,
                            CountryRepository countryRepo,
                            RegionRepository regionRepo,
                            CheckoutPricingService checkoutPricingService) {
        this.orderItemRepo = orderItemRepo;
        this.orderRepo = orderRepo;
        this.usersRepo = usersRepo;
        this.itemRepo = itemRepo;
        this.currencyRepo = currencyRepo;
        this.orderStatusRepo = orderStatusRepo;
        this.countryRepo = countryRepo;
        this.regionRepo = regionRepo;
        this.checkoutPricingService = checkoutPricingService;
    }

    /* ===============================
       STATUS HELPERS
       =============================== */

    private OrderStatus requireStatus(String code) {
        return orderStatusRepo.findByNameIgnoreCase(code)
                .orElseThrow(() -> new IllegalStateException("OrderStatus not found: " + code));
    }

    private String currentStatusCode(Order header) {
        if (header == null || header.getStatus() == null || header.getStatus().getName() == null)
            return "";
        return header.getStatus().getName().toUpperCase(Locale.ROOT);
    }

    private void flipStatus(OrderItem oi, String newStatusUpper) {
        Order header = oi.getOrder();
        if (header == null)
            throw new IllegalStateException("Missing order header");

        OrderStatus statusEntity = requireStatus(newStatusUpper);
        header.setStatus(statusEntity);

        header.setOrderDate(LocalDateTime.now());
        oi.setUpdatedAt(LocalDateTime.now());

        orderRepo.save(header);
        orderItemRepo.save(oi);
    }

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

    private Integer tryCapacity(Item item) {
        String[] names = {"getMaxParticipants", "getCapacity", "getSeats", "getQuantityLimit", "getStock"};
        for (String n : names) {
            try {
                Method m = item.getClass().getMethod(n);
                Object v = m.invoke(item);
                if (v instanceof Integer) return (Integer) v;
                if (v instanceof Number) return ((Number) v).intValue();
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
       CREATE ORDER (STRIPE) - activities single item
       =============================== */

    @Override
    public OrderItem createBookItem(Long userId, Long itemId, int quantity,
                                    String stripePaymentId, Long currencyId) {

        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (itemId == null) throw new IllegalArgumentException("itemId is required");
        if (quantity <= 0) throw new IllegalArgumentException("participants must be > 0");
        if (stripePaymentId == null || stripePaymentId.isBlank())
            throw new IllegalArgumentException("stripePaymentId is required");

        // Stripe validation
        try {
            var pi = com.stripe.model.PaymentIntent.retrieve(stripePaymentId);
            if (pi == null || !"succeeded".equalsIgnoreCase(pi.getStatus()))
                throw new IllegalStateException("Stripe payment not confirmed");
        } catch (Exception e) {
            throw new IllegalStateException("Stripe error: " + e.getMessage());
        }

        Users user = usersRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Item item = itemRepo.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        Currency currency = null;
        if (currencyId != null) {
            currency = currencyRepo.findById(currencyId)
                    .orElseThrow(() -> new IllegalArgumentException("Currency not found"));
        }

        // capacity
        Integer capacity = tryCapacity(item);
        if (capacity != null) {
            int already = orderItemRepo.sumQuantityByItemIdAndStatusNames(
                    itemId, List.of("COMPLETED")
            );
            int remaining = capacity - already;
            if (quantity > remaining)
                throw new IllegalStateException("Not enough seats available");
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

        return orderItemRepo.save(line);
    }

    /* ===============================
       CREATE ORDER (CASH) - activities single item
       =============================== */

    @Override
    public OrderItem createCashorderByBusiness(Long itemId, Long businessUserId,
                                               int quantity, boolean wasPaid, Long currencyId) {

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
            int already = orderItemRepo.sumQuantityByItemIdAndStatusNames(
                    itemId, List.of("COMPLETED")
            );
            int remaining = capacity - already;
            if (quantity > remaining)
                throw new IllegalStateException("Not enough seats available");
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
        var header = oi.getOrder();
        String curr = currentStatusCode(header);

        if ("COMPLETED".equals(curr))
            throw new IllegalStateException("Cannot cancel a completed order");

        if ("CANCELED".equals(curr)) return;

        flipStatus(oi, "CANCELED");
    }

    @Override
    public void resetToPending(Long orderItemId, Long actorId) {
        var oi = requireUserOwned(orderItemId, actorId);
        var header = oi.getOrder();
        String curr = currentStatusCode(header);

        if ("COMPLETED".equals(curr))
            throw new IllegalStateException("Cannot reset a completed order");

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

        if ("COMPLETED".equals(curr))
            throw new IllegalStateException("Cannot request cancel on a completed order");

        if ("CANCELED".equals(curr)) return;

        flipStatus(oi, "CANCEL_REQUESTED");
    }

    @Override
    public void approveCancel(Long orderItemId, Long businessId) {
        var oi = requireBusinessOwned(orderItemId, businessId);
        String curr = currentStatusCode(oi.getOrder());

        if ("COMPLETED".equals(curr))
            throw new IllegalStateException("Cannot approve cancel for completed order");

        if ("CANCELED".equals(curr)) return;

        flipStatus(oi, "CANCELED");
    }

    @Override
    public void rejectCancel(Long orderItemId, Long businessId) {
        var oi = requireBusinessOwned(orderItemId, businessId);
        String curr = currentStatusCode(oi.getOrder());

        if ("CANCELED".equals(curr))
            throw new IllegalStateException("Already canceled");

        if ("COMPLETED".equals(curr)) return;

        flipStatus(oi, "PENDING");
    }

    @Override
    public void markRefunded(Long orderItemId, Long businessId) {
        var oi = requireBusinessOwned(orderItemId, businessId);
        flipStatus(oi, "REFUNDED");
    }

    @Override
    public List<OrderItem> getordersByBusiness(Long businessId) {
        return orderItemRepo.findRichByBusinessId(businessId);
    }

    @Override
    public void markPaid(Long orderItemId, Long businessId) {
        var oi = orderItemRepo.findByIdAndBusiness(orderItemId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found or not yours"));

        var header = oi.getOrder();
        String curr = currentStatusCode(header);

        if ("COMPLETED".equals(curr)) return;

        header.setStatus(requireStatus("COMPLETED"));
        header.setOrderDate(LocalDateTime.now());
        oi.setUpdatedAt(LocalDateTime.now());

        orderRepo.save(header);
        orderItemRepo.save(oi);
    }

    @Override
    public void deleteordersByItemId(Long itemId) {
        orderItemRepo.deleteByItem_Id(itemId);
    }

    @Override
    public void rejectorder(Long orderItemId, Long businessId) {
        var oi = requireBusinessOwned(orderItemId, businessId);
        String curr = currentStatusCode(oi.getOrder());

        if ("COMPLETED".equals(curr))
            throw new IllegalStateException("Cannot reject completed order");

        if ("REJECTED".equals(curr)) return;

        flipStatus(oi, "REJECTED");
    }

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
       =============================== */

    @Override
    public CheckoutSummaryResponse checkout(Long userId, CheckoutRequest request) {

        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (request == null || request.getLines() == null || request.getLines().isEmpty()) {
            throw new IllegalArgumentException("Cart lines are required");
        }
        if (request.getCurrencyId() == null) {
            throw new IllegalArgumentException("currencyId is required");
        }
        if (request.getPaymentMethod() == null || request.getPaymentMethod().isBlank()) {
            throw new IllegalArgumentException("paymentMethod is required");
        }

        // Normalize paymentMethod
        String paymentMethod = request.getPaymentMethod().trim().toUpperCase();
        boolean isStripe = "STRIPE".equals(paymentMethod);

        // Stripe validation if needed
        if (isStripe) {
            String stripePaymentId = request.getStripePaymentId();
            if (stripePaymentId == null || stripePaymentId.isBlank()) {
                throw new IllegalArgumentException("stripePaymentId is required for STRIPE payment");
            }

            try {
                var pi = com.stripe.model.PaymentIntent.retrieve(stripePaymentId);
                if (pi == null || !"succeeded".equalsIgnoreCase(pi.getStatus())) {
                    throw new IllegalStateException("Stripe payment not confirmed");
                }
            } catch (Exception e) {
                throw new IllegalStateException("Stripe error: " + e.getMessage());
            }
        }

        Users user = usersRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Currency currency = currencyRepo.findById(request.getCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException("Currency not found"));

        List<CartLine> lines = request.getLines();

        // ---- Load items once, compute unitPrice/lineSubtotal, check capacity
        Map<Long, Item> itemCache = new HashMap<>();
        Long ownerProjectId = null;

        for (CartLine line : lines) {
            if (line.getItemId() == null) {
                throw new IllegalArgumentException("itemId is required in cart line");
            }
            if (line.getQuantity() <= 0) {
                throw new IllegalArgumentException("quantity must be > 0 for itemId = " + line.getItemId());
            }

            Item item = itemRepo.findById(line.getItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + line.getItemId()));
            itemCache.put(item.getId(), item);

            // ownerProject consistency (single app per cart)
            if (item.getOwnerProject() == null || item.getOwnerProject().getId() == null) {
                throw new IllegalStateException("Item " + item.getId() + " has no ownerProject");
            }
            Long opId = item.getOwnerProject().getId();
            if (ownerProjectId == null) {
                ownerProjectId = opId;
            } else if (!ownerProjectId.equals(opId)) {
                throw new IllegalArgumentException("All cart items must belong to the same app (ownerProjectId)");
            }

            // capacity / stock check
            Integer capacity = tryCapacity(item);
            if (capacity != null) {
                int already = orderItemRepo.sumQuantityByItemIdAndStatusNames(
                        item.getId(), List.of("COMPLETED")
                );
                int remaining = capacity - already;
                if (line.getQuantity() > remaining) {
                    throw new IllegalStateException("Not enough quantity available for itemId = " + item.getId());
                }
            }

            // compute and store prices in CartLine (so pricing service can use them)
            BigDecimal unit = resolveUnitPrice(item);
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(line.getQuantity()));
            line.setUnitPrice(unit);
            line.setLineSubtotal(lineTotal);
        }

        if (ownerProjectId == null) {
            throw new IllegalArgumentException("Could not resolve ownerProjectId from cart items");
        }

        // ---- Prepare shipping address entities (for persisting on Order)
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

        // ---- Delegate full pricing (shipping + tax + coupon) to CheckoutPricingService
        CheckoutSummaryResponse priced = checkoutPricingService.priceCheckout(
                ownerProjectId,
                request.getCurrencyId(),
                request
        );

        // ---- Create Order header using priced totals ----
        Order order = new Order();
        order.setUser(user);
        order.setStatus(requireStatus("PENDING"));
        order.setOrderDate(LocalDateTime.now());
        order.setCurrency(currency);
        order.setTotalPrice(priced.getGrandTotal());

        if (addr != null) {
            order.setShippingCountry(shippingCountry);
            order.setShippingRegion(shippingRegion);
            order.setShippingCity(addr.getCity());
            order.setShippingPostalCode(addr.getPostalCode());
            // note: if you want, you can also persist:
            order.setShippingMethodId(addr.getShippingMethodId());
            order.setShippingMethodName(addr.getShippingMethodName());
        }

        order.setShippingTotal(priced.getShippingTotal());
        order.setItemTaxTotal(priced.getItemTaxTotal());
        order.setShippingTaxTotal(priced.getShippingTaxTotal());
        order.setCouponCode(priced.getCouponCode());
        order.setCouponDiscount(priced.getCouponDiscount());

        order = orderRepo.save(order);

        // ---- Create OrderItem lines (price from CartLine / pricing) ----
        for (CartLine line : lines) {
            Item item = itemCache.get(line.getItemId());
            if (item == null) {
                // Should not happen, but safe-guard
                item = itemRepo.findById(line.getItemId())
                        .orElseThrow(() -> new IllegalArgumentException("Item not found: " + line.getItemId()));
            }

            OrderItem oi = new OrderItem();
            oi.setOrder(order);
            oi.setItem(item);
            oi.setUser(user);
            oi.setCurrency(currency);
            oi.setQuantity(line.getQuantity());
            oi.setPrice(line.getUnitPrice()); // must match pricing
            oi.setCreatedAt(LocalDateTime.now());
            oi.setUpdatedAt(LocalDateTime.now());

            orderItemRepo.save(oi);
        }

        // ---- Attach order id/date to response and return ----
        priced.setOrderId(order.getId());
        priced.setOrderDate(order.getOrderDate());
        return priced;
    }
}
