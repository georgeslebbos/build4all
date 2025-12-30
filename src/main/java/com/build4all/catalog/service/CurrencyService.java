package com.build4all.catalog.service;

import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.repository.CurrencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CurrencyService {

    private final CurrencyRepository currencyRepository;

    public CurrencyService(CurrencyRepository currencyRepository) {
        this.currencyRepository = currencyRepository;
    }

    @Transactional
    public void ensureDefaultCurrencies() {

        // Keep insertion order stable (nice for logs/debug)
        Map<String, String[]> defaults = new LinkedHashMap<>();

        // currencyType, code, symbol
        defaults.put("US_DOLLAR", new String[]{"USD", "$"});
        defaults.put("EURO", new String[]{"EUR", "€"});
        defaults.put("CANADIAN_DOLLAR", new String[]{"CAD", "C$"});

        for (Map.Entry<String, String[]> e : defaults.entrySet()) {
            String currencyType = e.getKey();
            String code = e.getValue()[0];
            String symbol = e.getValue()[1];

            seedByCode(currencyType, symbol, code);
        }
    }

    private void seedByCode(String currencyType, String symbol, String code) {

        currencyRepository.findByCodeIgnoreCase(code).ifPresentOrElse(existing -> {

            boolean updated = false;

            // keep DB consistent
            if (existing.getCurrencyType() == null || !currencyType.equals(existing.getCurrencyType())) {
                existing.setCurrencyType(currencyType);
                updated = true;
            }

            if (existing.getSymbol() == null || !symbol.equals(existing.getSymbol())) {
                existing.setSymbol(symbol);
                updated = true;
            }

            // code should already match, but keep it safe
            if (existing.getCode() == null || !code.equalsIgnoreCase(existing.getCode())) {
                existing.setCode(code);
                updated = true;
            }

            if (updated) currencyRepository.save(existing);

        }, () -> {
            // insert only if code does not exist
            currencyRepository.save(new Currency(currencyType, symbol, code));
        });
    }
}
