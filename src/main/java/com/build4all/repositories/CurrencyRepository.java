package com.build4all.repositories;

import java.util.Optional; 
// ✅ Correct
import com.build4all.entities.Currency;


import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyRepository extends JpaRepository<Currency, Long> {
    
    boolean existsByCurrencyType(String currencyType);

    Optional<Currency> findByCurrencyType(String currencyType);

}
