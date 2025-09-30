package com.build4all.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ItemIconEnum {

    // SPORTS
    FOOTBALL_BALL("basketball"),
    SPA("leaf"),
    DUMBBELL("barbell"),
    TREE("tree"),
    HORSE("paw"),
    FISH("fish"),

    // MUSIC
    MUSIC("musical-notes"),
    MUSIC_NOTE("musical-note"),
    HEADPHONES("headset"),

    // ART
    PALETTE("color-palette"),
    SCISSORS("color-palette"),
    YARN("color-palette"),
    PEN_NIB("color-palette"),

    // TECH
    CODE("code-slash"),
    ROBOT("hardware-chip"),
    CUBE("cube"),
    FLASK("flask"),

    // FITNESS
    SELF_DEFENSE("shield"),
    MEDITATION("moon"),

    // COOKING
    RESTAURANT("restaurant"),

    // TRAVEL
    GLOBE("globe"),
    HIKING_BOOTS("walk"),

    // GAMING
    GAMEPAD("game-controller"),
    CHESS("grid"),

    // THEATER
    THEATER_MASKS("happy"),
    LAUGH("happy-outline"),
    BOOK_OPEN("book"),

    // LANGUAGE
    LANGUAGE("globe"),
    MICROPHONE("mic"),
    PEN("pencil"),

    // PHOTOGRAPHY
    CAMERA("camera"),
    VIDEO("videocam"),

    // DIY
    HAMMER("construct"),
    TOOLS("construct"),
    RULER("construct"),

    // BEAUTY
    LIPSTICK("color-wand"),
    HEART("heart"),

    // FINANCE
    CHART_LINE("stats-chart"),
    BRIEFCASE("briefcase"),

    // OTHER
    DOG("paw"),
    PODCAST("mic-circle"),
    MAGIC("sparkles"),
    STAR("star"),
    HAND_HOLDING_HEART("heart"),
    CLOCK("time"),
    BINOCULARS("search"),
    PEOPLE_ARROWS("people");

    private final String iconName;

    ItemIconEnum(String iconName) { this.iconName = iconName; }

    @JsonValue
    public String getIconName() { return iconName; }
}
