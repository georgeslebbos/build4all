package com.build4all.enums;

public enum InterestIconEnum {
    BASKETBALL("basketball"),          // Ionicons ✅
    MUSIC("musical-notes"),            // Ionicons ✅
    ART("color-palette"),              // Ionicons ✅
    TECH("laptop"),                    // Ionicons ✅
    FITNESS("barbell"),                 // Ionicons ✅
    COOKING("restaurant"),             // Ionicons ✅
    TRAVEL("airplane"),                 // Ionicons ✅
    GAMING("game-controller"),         // Ionicons ✅
    THEATER("happy"),                   // closest Ionicons option
    LANGUAGE("language"),               // Ionicons ✅
    PHOTOGRAPHY("camera"),              // Ionicons ✅
    DIY("construct"),                   // Ionicons ✅
    BEAUTY("rose"),                     // Ionicons ✅
    FINANCE("wallet"),                  // Ionicons ✅
    OTHER("star");                      // Ionicons ✅

    private final String iconName;

    InterestIconEnum(String iconName) {
        this.iconName = iconName;
    }

    public String getIconName() {
        return iconName;
    }
}
