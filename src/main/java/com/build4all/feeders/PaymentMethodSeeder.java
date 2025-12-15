package com.build4all.feeders;

import com.build4all.payment.domain.PaymentMethod;
import com.build4all.payment.repository.PaymentMethodRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class PaymentMethodSeeder implements CommandLineRunner {

    private final PaymentMethodRepository repo;

    public PaymentMethodSeeder(PaymentMethodRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(String... args) {
        upsert("STRIPE");
        upsert("CASH");
        upsert("PAYPAL"); // plugin exists (template). You can keep it enabled/disabled.
    }

    private void upsert(String name) {
        repo.findByNameIgnoreCase(name)
                .orElseGet(() -> repo.save(new PaymentMethod(name.toUpperCase(), true)));
    }
}

