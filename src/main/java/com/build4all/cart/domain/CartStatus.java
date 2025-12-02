package com.build4all.cart.domain;

public enum CartStatus {
    ACTIVE,           // current cart of user
    CONVERTED,        // turned into order
    ABANDONED,        // old, not used anymore
    EXPIRED           // auto expired
}
