package com.build4all.feeders;

import com.build4all.catalog.domain.Country;
import com.build4all.catalog.domain.Region;
import com.build4all.catalog.repository.CountryRepository;
import com.build4all.catalog.repository.RegionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

@Configuration
public class CountriesAndLebanonRegionsSeeder {

    private record CountrySeed(String iso2, String name) {}
    private record RegionSeed(String countryIso2, String code, String name) {}

    @Bean
    public CommandLineRunner seedCountriesAndRegions(CountryRepository countryRepo,
                                                     RegionRepository regionRepo) {
        return args -> {
            System.out.println("✅ Country/Region seeder running...");

            // ---- 1) ALL COUNTRIES (UN-like) EXCEPT ISRAEL (IL) ----
            List<CountrySeed> countries = List.of(
                    new CountrySeed("AF", "Afghanistan"),
                    new CountrySeed("AL", "Albania"),
                    new CountrySeed("DZ", "Algeria"),
                    new CountrySeed("AD", "Andorra"),
                    new CountrySeed("AO", "Angola"),
                    new CountrySeed("AG", "Antigua and Barbuda"),
                    new CountrySeed("AR", "Argentina"),
                    new CountrySeed("AM", "Armenia"),
                    new CountrySeed("AU", "Australia"),
                    new CountrySeed("AT", "Austria"),
                    new CountrySeed("AZ", "Azerbaijan"),
                    new CountrySeed("BS", "Bahamas"),
                    new CountrySeed("BH", "Bahrain"),
                    new CountrySeed("BD", "Bangladesh"),
                    new CountrySeed("BB", "Barbados"),
                    new CountrySeed("BY", "Belarus"),
                    new CountrySeed("BE", "Belgium"),
                    new CountrySeed("BZ", "Belize"),
                    new CountrySeed("BJ", "Benin"),
                    new CountrySeed("BT", "Bhutan"),
                    new CountrySeed("BO", "Bolivia"),
                    new CountrySeed("BA", "Bosnia and Herzegovina"),
                    new CountrySeed("BW", "Botswana"),
                    new CountrySeed("BR", "Brazil"),
                    new CountrySeed("BN", "Brunei Darussalam"),
                    new CountrySeed("BG", "Bulgaria"),
                    new CountrySeed("BF", "Burkina Faso"),
                    new CountrySeed("BI", "Burundi"),
                    new CountrySeed("CV", "Cabo Verde"),
                    new CountrySeed("KH", "Cambodia"),
                    new CountrySeed("CM", "Cameroon"),
                    new CountrySeed("CA", "Canada"),
                    new CountrySeed("CF", "Central African Republic"),
                    new CountrySeed("TD", "Chad"),
                    new CountrySeed("CL", "Chile"),
                    new CountrySeed("CN", "China"),
                    new CountrySeed("CO", "Colombia"),
                    new CountrySeed("KM", "Comoros"),
                    new CountrySeed("CG", "Congo"),
                    new CountrySeed("CD", "Congo, Democratic Republic of"),
                    new CountrySeed("CR", "Costa Rica"),
                    new CountrySeed("CI", "Côte d’Ivoire"),
                    new CountrySeed("HR", "Croatia"),
                    new CountrySeed("CU", "Cuba"),
                    new CountrySeed("CY", "Cyprus"),
                    new CountrySeed("CZ", "Czechia"),
                    new CountrySeed("DK", "Denmark"),
                    new CountrySeed("DJ", "Djibouti"),
                    new CountrySeed("DM", "Dominica"),
                    new CountrySeed("DO", "Dominican Republic"),
                    new CountrySeed("EC", "Ecuador"),
                    new CountrySeed("EG", "Egypt"),
                    new CountrySeed("SV", "El Salvador"),
                    new CountrySeed("GQ", "Equatorial Guinea"),
                    new CountrySeed("ER", "Eritrea"),
                    new CountrySeed("EE", "Estonia"),
                    new CountrySeed("SZ", "Eswatini"),
                    new CountrySeed("ET", "Ethiopia"),
                    new CountrySeed("FJ", "Fiji"),
                    new CountrySeed("FI", "Finland"),
                    new CountrySeed("FR", "France"),
                    new CountrySeed("GA", "Gabon"),
                    new CountrySeed("GM", "Gambia"),
                    new CountrySeed("GE", "Georgia"),
                    new CountrySeed("DE", "Germany"),
                    new CountrySeed("GH", "Ghana"),
                    new CountrySeed("GR", "Greece"),
                    new CountrySeed("GD", "Grenada"),
                    new CountrySeed("GT", "Guatemala"),
                    new CountrySeed("GN", "Guinea"),
                    new CountrySeed("GW", "Guinea-Bissau"),
                    new CountrySeed("GY", "Guyana"),
                    new CountrySeed("HT", "Haiti"),
                    new CountrySeed("HN", "Honduras"),
                    new CountrySeed("HU", "Hungary"),
                    new CountrySeed("IS", "Iceland"),
                    new CountrySeed("IN", "India"),
                    new CountrySeed("ID", "Indonesia"),
                    new CountrySeed("IR", "Iran"),
                    new CountrySeed("IQ", "Iraq"),
                    new CountrySeed("IE", "Ireland"),
                    new CountrySeed("IT", "Italy"),
                    new CountrySeed("JM", "Jamaica"),
                    new CountrySeed("JP", "Japan"),
                    new CountrySeed("JO", "Jordan"),
                    new CountrySeed("KZ", "Kazakhstan"),
                    new CountrySeed("KE", "Kenya"),
                    new CountrySeed("KI", "Kiribati"),
                    new CountrySeed("KP", "Korea, Democratic People’s Republic of"),
                    new CountrySeed("KR", "Korea, Republic of"),
                    new CountrySeed("KW", "Kuwait"),
                    new CountrySeed("KG", "Kyrgyzstan"),
                    new CountrySeed("LA", "Lao People’s Democratic Republic"),
                    new CountrySeed("LV", "Latvia"),
                    new CountrySeed("LB", "Lebanon"),
                    new CountrySeed("LS", "Lesotho"),
                    new CountrySeed("LR", "Liberia"),
                    new CountrySeed("LY", "Libya"),
                    new CountrySeed("LI", "Liechtenstein"),
                    new CountrySeed("LT", "Lithuania"),
                    new CountrySeed("LU", "Luxembourg"),
                    new CountrySeed("MG", "Madagascar"),
                    new CountrySeed("MW", "Malawi"),
                    new CountrySeed("MY", "Malaysia"),
                    new CountrySeed("MV", "Maldives"),
                    new CountrySeed("ML", "Mali"),
                    new CountrySeed("MT", "Malta"),
                    new CountrySeed("MH", "Marshall Islands"),
                    new CountrySeed("MR", "Mauritania"),
                    new CountrySeed("MU", "Mauritius"),
                    new CountrySeed("MX", "Mexico"),
                    new CountrySeed("FM", "Micronesia"),
                    new CountrySeed("MD", "Moldova"),
                    new CountrySeed("MC", "Monaco"),
                    new CountrySeed("MN", "Mongolia"),
                    new CountrySeed("ME", "Montenegro"),
                    new CountrySeed("MA", "Morocco"),
                    new CountrySeed("MZ", "Mozambique"),
                    new CountrySeed("MM", "Myanmar"),
                    new CountrySeed("NA", "Namibia"),
                    new CountrySeed("NR", "Nauru"),
                    new CountrySeed("NP", "Nepal"),
                    new CountrySeed("NL", "Netherlands"),
                    new CountrySeed("NZ", "New Zealand"),
                    new CountrySeed("NI", "Nicaragua"),
                    new CountrySeed("NE", "Niger"),
                    new CountrySeed("NG", "Nigeria"),
                    new CountrySeed("MK", "North Macedonia"),
                    new CountrySeed("NO", "Norway"),
                    new CountrySeed("OM", "Oman"),
                    new CountrySeed("PK", "Pakistan"),
                    new CountrySeed("PW", "Palau"),
                    new CountrySeed("PS", "Palestine"),
                    new CountrySeed("PA", "Panama"),
                    new CountrySeed("PG", "Papua New Guinea"),
                    new CountrySeed("PY", "Paraguay"),
                    new CountrySeed("PE", "Peru"),
                    new CountrySeed("PH", "Philippines"),
                    new CountrySeed("PL", "Poland"),
                    new CountrySeed("PT", "Portugal"),
                    new CountrySeed("QA", "Qatar"),
                    new CountrySeed("RO", "Romania"),
                    new CountrySeed("RU", "Russian Federation"),
                    new CountrySeed("RW", "Rwanda"),
                    new CountrySeed("KN", "Saint Kitts and Nevis"),
                    new CountrySeed("LC", "Saint Lucia"),
                    new CountrySeed("VC", "Saint Vincent and the Grenadines"),
                    new CountrySeed("WS", "Samoa"),
                    new CountrySeed("SM", "San Marino"),
                    new CountrySeed("ST", "Sao Tome and Principe"),
                    new CountrySeed("SA", "Saudi Arabia"),
                    new CountrySeed("SN", "Senegal"),
                    new CountrySeed("RS", "Serbia"),
                    new CountrySeed("SC", "Seychelles"),
                    new CountrySeed("SL", "Sierra Leone"),
                    new CountrySeed("SG", "Singapore"),
                    new CountrySeed("SK", "Slovakia"),
                    new CountrySeed("SI", "Slovenia"),
                    new CountrySeed("SB", "Solomon Islands"),
                    new CountrySeed("SO", "Somalia"),
                    new CountrySeed("ZA", "South Africa"),
                    new CountrySeed("SS", "South Sudan"),
                    new CountrySeed("ES", "Spain"),
                    new CountrySeed("LK", "Sri Lanka"),
                    new CountrySeed("SD", "Sudan"),
                    new CountrySeed("SR", "Suriname"),
                    new CountrySeed("SE", "Sweden"),
                    new CountrySeed("CH", "Switzerland"),
                    new CountrySeed("SY", "Syrian Arab Republic"),
                    new CountrySeed("TJ", "Tajikistan"),
                    new CountrySeed("TZ", "Tanzania, United Republic of"),
                    new CountrySeed("TH", "Thailand"),
                    new CountrySeed("TL", "Timor-Leste"),
                    new CountrySeed("TG", "Togo"),
                    new CountrySeed("TO", "Tonga"),
                    new CountrySeed("TT", "Trinidad and Tobago"),
                    new CountrySeed("TN", "Tunisia"),
                    new CountrySeed("TR", "Türkiye"),
                    new CountrySeed("TM", "Turkmenistan"),
                    new CountrySeed("TV", "Tuvalu"),
                    new CountrySeed("UG", "Uganda"),
                    new CountrySeed("UA", "Ukraine"),
                    new CountrySeed("AE", "United Arab Emirates"),
                    new CountrySeed("GB", "United Kingdom"),
                    new CountrySeed("US", "United States of America"),
                    new CountrySeed("UY", "Uruguay"),
                    new CountrySeed("UZ", "Uzbekistan"),
                    new CountrySeed("VU", "Vanuatu"),
                    new CountrySeed("VE", "Venezuela"),
                    new CountrySeed("VN", "Viet Nam"),
                    new CountrySeed("YE", "Yemen"),
                    new CountrySeed("ZM", "Zambia"),
                    new CountrySeed("ZW", "Zimbabwe")
                    // intentionally no "IL"
            );

            Map<String, Country> iso2ToCountry = new HashMap<>();

            for (CountrySeed c : countries) {
                Country country = countryRepo.findByIso2CodeIgnoreCase(c.iso2())
                        .orElseGet(() -> {
                            Country nc = new Country();
                            nc.setIso2Code(c.iso2());
                            nc.setName(c.name());
                            nc.setActive(true);
                            return countryRepo.save(nc);
                        });
                iso2ToCountry.put(c.iso2().toUpperCase(), country);
            }

            // ---- 2) REGIONS (only Mouhafazat for Lebanon) ----

            List<RegionSeed> regions = List.of(
                    new RegionSeed("LB", "BEIRUT", "Beirut"),
                    new RegionSeed("LB", "MOUNT_LEBANON", "Mount Lebanon"),
                    new RegionSeed("LB", "NORTH", "North Lebanon"),
                    new RegionSeed("LB", "AKKAR", "Akkar"),
                    new RegionSeed("LB", "BEKAA", "Bekaa"),
                    new RegionSeed("LB", "BAALBEK_HERMEL", "Baalbek-Hermel"),
                    new RegionSeed("LB", "SOUTH", "South Lebanon"),
                    new RegionSeed("LB", "NABATIEH", "Nabatieh")
            );

            for (RegionSeed r : regions) {
                Country country = iso2ToCountry.get(r.countryIso2().toUpperCase());
                if (country == null) {
                    System.out.println("⚠️ Country not found for region seed: " + r);
                    continue;
                }

                boolean exists = regionRepo
                        .findByCountryAndCodeIgnoreCase(country, r.code())
                        .isPresent();

                if (!exists) {
                    Region region = new Region();
                    region.setCountry(country);
                    region.setCode(r.code());
                    region.setName(r.name());
                    region.setActive(true);
                    regionRepo.save(region);
                    System.out.println("   • inserted Region: " + r.code() + " (" + r.name() + ")");
                }
            }

            System.out.println("✅ Country/Region seeding done.");
        };
    }
}