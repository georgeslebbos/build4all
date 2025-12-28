package com.build4all.feeders;

import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.repository.CurrencyRepository;
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
        //seed("DOLLAR", "$", "USD");
        //seed("EURO", "€", "EUR");
        //seed("CAD", "C$", "CAD");

        // Major / Global
        seed("US_DOLLAR", "$", "USD");
        seed("EURO", "€", "EUR");
        seed("BRITISH_POUND", "£", "GBP");
        seed("JAPANESE_YEN", "¥", "JPY");
        seed("SWISS_FRANC", "CHF", "CHF");
        seed("CANADIAN_DOLLAR", "C$", "CAD");
        seed("AUSTRALIAN_DOLLAR", "A$", "AUD");

// Middle East
        seed("LEBANESE_POUND", "L£", "LBP");
        seed("SAUDI_RIYAL", "﷼", "SAR");
        seed("UAE_DIRHAM", "د.إ", "AED");
        seed("QATARI_RIYAL", "﷼", "QAR");
        seed("KUWAITI_DINAR", "د.ك", "KWD");
        seed("OMANI_RIAL", "﷼", "OMR");
        seed("BAHRAINI_DINAR", ".د.ب", "BHD");
        seed("JORDANIAN_DINAR", "د.ا", "JOD");

// Asia
        seed("CHINESE_YUAN", "¥", "CNY");
        seed("INDIAN_RUPEE", "₹", "INR");
        seed("PAKISTANI_RUPEE", "₨", "PKR");
        seed("SOUTH_KOREAN_WON", "₩", "KRW");
        seed("SINGAPORE_DOLLAR", "S$", "SGD");

// Africa
        seed("EGYPTIAN_POUND", "E£", "EGP");
        seed("MOROCCAN_DIRHAM", "د.م.", "MAD");
        seed("TUNISIAN_DINAR", "د.ت", "TND");
        seed("ALGERIAN_DINAR", "د.ج", "DZD");
        seed("SOUTH_AFRICAN_RAND", "R", "ZAR");

// Europe (non-Euro)
        seed("SWEDISH_KRONA", "kr", "SEK");
        seed("NORWEGIAN_KRONE", "kr", "NOK");
        seed("DANISH_KRONE", "kr", "DKK");
        seed("POLISH_ZLOTY", "zł", "PLN");
        seed("CZECH_KORUNA", "Kč", "CZK");

// Americas
        seed("MEXICAN_PESO", "$", "MXN");
        seed("BRAZILIAN_REAL", "R$", "BRL");
        seed("ARGENTINE_PESO", "$", "ARS");
        seed("CHILEAN_PESO", "$", "CLP");


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
