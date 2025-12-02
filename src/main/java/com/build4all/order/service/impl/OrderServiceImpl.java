package com.build4all.order.service.impl;

import com.build4all.order.domain.Order;
import com.build4all.order.domain.OrderStatus;
import com.build4all.order.domain.OrderItem;
import com.build4all.order.repository.OrderRepository;
import com.build4all.order.repository.OrderStatusRepository;
import com.build4all.order.repository.OrderItemRepository;
import com.build4all.order.service.OrderService;
import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.domain.Item;
import com.build4all.catalog.repository.CurrencyRepository;
import com.build4all.catalog.repository.ItemRepository;
import com.build4all.user.domain.Users;
import com.build4all.user.repository.UsersRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
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

    public OrderServiceImpl(OrderItemRepository orderItemRepo,
                            OrderRepository orderRepo,
                            UsersRepository usersRepo,
                            ItemRepository itemRepo,
                            CurrencyRepository currencyRepo,
                            OrderStatusRepository orderStatusRepo) {
        this.orderItemRepo = orderItemRepo;
        this.orderRepo = orderRepo;
        this.usersRepo = usersRepo;
        this.itemRepo = itemRepo;
        this.currencyRepo = currencyRepo;
        this.orderStatusRepo = orderStatusRepo;
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
       CREATE ORDER (STRIPE)
       =============================== */

    @Override
    public OrderItem createBookItem(Long userId, Long itemId, int quantity,
                                    String stripePaymentId, Long currencyId) {

        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (itemId == null) throw new IllegalArgumentException("itemId is required");
        if (quantity <= 0) throw new IllegalArgumentException("participants must be > 0");
        if (stripePaymentId == null || stripePaymentId.isBlank())
            throw new IllegalArgumentException("stripePaymentId is required");
        
        boolean DEV_SKIP_STRIPE_CHECK = true;

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
       CREATE ORDER (CASH)
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
}
