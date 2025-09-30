package com.build4all.config;

import com.build4all.entities.Currency;
import com.build4all.repositories.CurrencyRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class CurrencySeeder {

    private final CurrencyRepository currencyRepository;

    public CurrencySeeder(CurrencyRepository currencyRepository) {
        this.currencyRepository = currencyRepository;
    }

    @PostConstruct
    public void seedCurrencies() {
        seed("DOLLAR", "$", "USD");
        seed("EURO", "€", "EUR");
        seed("CAD", "C$", "CAD");
    }

    private void seed(String currencyType, String symbol, String code) {
        currencyRepository.findByCurrencyType(currencyType)
            .ifPresentOrElse(existing -> {
                boolean updated = false;

                if (!symbol.equals(existing.getSymbol())) {
                    existing.setSymbol(symbol);
                    updated = true;
                }

                if (existing.getCode() == null || !code.equals(existing.getCode())) {
                    existing.setCode(code);
                    updated = true;
                }

                if (updated) {
                    currencyRepository.save(existing);
                }
            }, () -> {
                currencyRepository.save(new Currency(currencyType, symbol, code));
            });
    }
}
