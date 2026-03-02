package com.build4all.payment.web;

import com.build4all.payment.domain.PaymentMethod;
import com.build4all.payment.repository.PaymentMethodRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/superadmin/payment-methods")
public class PaymentMethodPlatformController {

    private final PaymentMethodRepository repo;

    public PaymentMethodPlatformController(PaymentMethodRepository repo) {
        this.repo = repo;
    }

    @PreAuthorize("hasRole('OWNER') or hasRole('SUPER_ADMIN')")
    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(repo.findAll());
    }

    @PreAuthorize("hasRole('OWNER') or hasRole('SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody PaymentMethod body) {

        if (body.getName() == null || body.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }

        String name = body.getName().trim().toUpperCase(Locale.ROOT);

        if (repo.findByNameIgnoreCase(name).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Payment method exists: " + name));
        }

        PaymentMethod pm = new PaymentMethod();
        pm.setName(name);
        pm.setEnabled(body.isEnabled());

        return ResponseEntity.status(201).body(repo.save(pm));
    }

    @PreAuthorize("hasRole('OWNER') or hasRole('SUPER_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody PaymentMethod body) {

        PaymentMethod pm = repo.findById(id).orElse(null);
        if (pm == null) return ResponseEntity.status(404).body(Map.of("error", "not found"));

        if (body.getName() != null && !body.getName().isBlank()) {
            pm.setName(body.getName().trim().toUpperCase(Locale.ROOT));
        }
        pm.setEnabled(body.isEnabled());

        return ResponseEntity.ok(repo.save(pm));
    }

    @PreAuthorize("hasRole('OWNER') or hasRole('SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.status(404).body(Map.of("error", "not found"));
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}