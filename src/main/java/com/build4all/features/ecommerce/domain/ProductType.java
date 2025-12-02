package com.build4all.features.ecommerce.domain;

public enum ProductType {
    SIMPLE,    // normal product, one price
    VARIABLE,  // later: variations (size/color)
    GROUPED,   // group/bundle of items
    EXTERNAL   // affiliate / external link
}
