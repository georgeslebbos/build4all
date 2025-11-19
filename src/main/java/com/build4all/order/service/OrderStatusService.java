package com.build4all.order.service;

import com.build4all.order.domain.OrderStatus;
import com.build4all.order.repository.OrderStatusRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OrderStatusService {

    private final OrderStatusRepository repo;

    public OrderStatusService(OrderStatusRepository repo) {
        this.repo = repo;
    }

    public List<OrderStatus> findAll() { return repo.findAll(); }
    public Optional<OrderStatus> findById(Long id) { return repo.findById(id); }
    public OrderStatus save(OrderStatus status) { return repo.save(status); }
    public void delete(Long id) { repo.deleteById(id); }
}
