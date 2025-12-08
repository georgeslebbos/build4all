package com.build4all.catalog.dto;

public class CurrencyDTO {

    private Long id;
    private String currencyType; // DOLLAR / EURO / CAD
    private String code;         // USD / EUR / CAD
    private String symbol;       // $, €, C$

    public CurrencyDTO() {}

    public CurrencyDTO(Long id, String currencyType, String code, String symbol) {
        this.id = id;
        this.currencyType = currencyType;
        this.code = code;
        this.symbol = symbol;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCurrencyType() { return currencyType; }
    public void setCurrencyType(String currencyType) { this.currencyType = currencyType; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
}
