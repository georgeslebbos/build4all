package com.build4all.feeders;

import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.repository.CurrencyRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CurrencySeeder {

    private final CurrencyRepository currencyRepository;

    public CurrencySeeder(CurrencyRepository currencyRepository) {
        this.currencyRepository = currencyRepository;
    }

    @PostConstruct
    @Transactional
    public void seedCurrencies() {

        // Major / Global
        seed("USD", "US_DOLLAR", "$");
        seed("EUR", "EURO", "€");
        seed("GBP", "BRITISH_POUND", "£");
        seed("JPY", "JAPANESE_YEN", "¥");
        seed("CHF", "SWISS_FRANC", "CHF");
        seed("CAD", "CANADIAN_DOLLAR", "C$");
        seed("AUD", "AUSTRALIAN_DOLLAR", "A$");

        // Middle East
        seed("LBP", "LEBANESE_POUND", "L£");
        seed("SAR", "SAUDI_RIYAL", "﷼");
        seed("AED", "UAE_DIRHAM", "د.إ");
        seed("QAR", "QATARI_RIYAL", "﷼");
        seed("KWD", "KUWAITI_DINAR", "د.ك");
        seed("OMR", "OMANI_RIAL", "﷼");
        seed("BHD", "BAHRAINI_DINAR", ".د.ب");
        seed("JOD", "JORDANIAN_DINAR", "د.ا");

        // Asia
        seed("CNY", "CHINESE_YUAN", "¥");
        seed("INR", "INDIAN_RUPEE", "₹");
        seed("PKR", "PAKISTANI_RUPEE", "₨");
        seed("KRW", "SOUTH_KOREAN_WON", "₩");
        seed("SGD", "SINGAPORE_DOLLAR", "S$");

        // Africa
        seed("EGP", "EGYPTIAN_POUND", "E£");
        seed("MAD", "MOROCCAN_DIRHAM", "د.م.");
        seed("TND", "TUNISIAN_DINAR", "د.ت");
        seed("DZD", "ALGERIAN_DINAR", "د.ج");
        seed("ZAR", "SOUTH_AFRICAN_RAND", "R");

        // Europe (non-Euro)
        seed("SEK", "SWEDISH_KRONA", "kr");
        seed("NOK", "NORWEGIAN_KRONE", "kr");
        seed("DKK", "DANISH_KRONE", "kr");
        seed("PLN", "POLISH_ZLOTY", "zł");
        seed("CZK", "CZECH_KORUNA", "Kč");

        // Americas
        seed("MXN", "MEXICAN_PESO", "$");
        seed("BRL", "BRAZILIAN_REAL", "R$");
        seed("ARS", "ARGENTINE_PESO", "$");
        seed("CLP", "CHILEAN_PESO", "$");
    }

    private void seed(String code, String currencyType, String symbol) {

        // ✅ 1) Look up by UNIQUE KEY (code)
    	currencyRepository.findByCodeIgnoreCase(code).ifPresentOrElse(existing -> {

            boolean updated = false;

            if (existing.getCurrencyType() == null || !currencyType.equals(existing.getCurrencyType())) {
                existing.setCurrencyType(currencyType);
                updated = true;
            }

            if (existing.getSymbol() == null || !symbol.equals(existing.getSymbol())) {
                existing.setSymbol(symbol);
                updated = true;
            }

            // code already matches, since we searched by it
            if (updated) currencyRepository.save(existing);

        }, () -> {

            // ✅ 2) Insert safely (in case two threads seed at once)
            try {
                Currency c = new Currency(currencyType, symbol, code);
                currencyRepository.saveAndFlush(c);
            } catch (DataIntegrityViolationException ignored) {
                // Another seeder/thread inserted it first → ignore
            }
        });
    }
}
