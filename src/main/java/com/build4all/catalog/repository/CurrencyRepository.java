package com.build4all.catalog.repository;

import java.util.Optional; 
// ✅ Correct
import com.build4all.catalog.domain.Currency;


import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyRepository extends JpaRepository<Currency, Long> {
    
    boolean existsByCurrencyType(String currencyType);

    Optional<Currency> findByCurrencyType(String currencyType);

    Optional<Currency> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);

}
