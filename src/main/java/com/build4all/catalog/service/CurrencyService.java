package com.build4all.catalog.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.repository.CurrencyRepository;

import java.util.Map;

@Service
public class CurrencyService {

    @Autowired
    private CurrencyRepository currencyRepository;

    public void ensureDefaultCurrencies() {
       
        Map<String, String[]> defaultCurrencies = Map.of(
            "DOLLAR", new String[]{"USD", "$"},
            "EURO",   new String[]{"EUR", "€"},
            "CAD",    new String[]{"CAD", "C$"}
        );

        for (Map.Entry<String, String[]> entry : defaultCurrencies.entrySet()) {
            String type = entry.getKey();
            String code = entry.getValue()[0];
            String symbol = entry.getValue()[1];

            currencyRepository.findByCurrencyType(type).ifPresentOrElse(
                existing -> {
                    boolean updated = false;

                    if (!existing.getSymbol().equals(symbol)) {
                        existing.setSymbol(symbol);
                        updated = true;
                    }

                    if (existing.getCode() == null || !existing.getCode().equals(code)) {
                        existing.setCode(code);
                        updated = true;
                    }

                    if (updated) {
                        currencyRepository.save(existing);
                    }
                },
                () -> {
                    Currency currency = new Currency(type, symbol, code); 
                    currencyRepository.save(currency);
                }
            );
        }
    }
}
