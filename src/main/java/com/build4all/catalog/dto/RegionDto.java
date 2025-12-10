package com.build4all.catalog.dto;

public class RegionDto {

    private Long id;
    private String code;
    private String name;
    private boolean active;

    private Long countryId;
    private String countryIso2Code;
    private String countryIso3Code;
    private String countryName;

    public RegionDto() {}

    public RegionDto(Long id, String code, String name, boolean active,
                     Long countryId, String countryIso2Code, String countryIso3Code, String countryName) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.active = active;
        this.countryId = countryId;
        this.countryIso2Code = countryIso2Code;
        this.countryIso3Code = countryIso3Code;
        this.countryName = countryName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Long getCountryId() { return countryId; }
    public void setCountryId(Long countryId) { this.countryId = countryId; }

    public String getCountryIso2Code() { return countryIso2Code; }
    public void setCountryIso2Code(String countryIso2Code) { this.countryIso2Code = countryIso2Code; }

    public String getCountryIso3Code() { return countryIso3Code; }
    public void setCountryIso3Code(String countryIso3Code) { this.countryIso3Code = countryIso3Code; }

    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }
}
