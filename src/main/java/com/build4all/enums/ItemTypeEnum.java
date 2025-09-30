package com.build4all.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum ItemTypeEnum {

    FOOTBALL(InterestEnum.SPORTS),
    YOGA(InterestEnum.SPORTS),
    MARTIAL_ARTS(InterestEnum.SPORTS),
    HIKING(InterestEnum.SPORTS),
    HORSEBACK_RIDING(InterestEnum.SPORTS),
    FISHING(InterestEnum.SPORTS),

    MUSIC(InterestEnum.MUSIC),
    DANCE(InterestEnum.MUSIC),
    MUSIC_PRODUCTION(InterestEnum.MUSIC),

    ART(InterestEnum.ART),
    SCULPTING(InterestEnum.ART),
    KNITTING(InterestEnum.ART),
    CALLIGRAPHY(InterestEnum.ART),

    CODING(InterestEnum.TECH),
    ROBOTICS(InterestEnum.TECH),
    THREE_D_PRINTING(InterestEnum.TECH),
    SCIENCE_EXPERIMENTS(InterestEnum.TECH),

    FITNESS(InterestEnum.FITNESS),
    SELF_DEFENSE(InterestEnum.FITNESS),
    MEDITATION(InterestEnum.FITNESS),

    COOKING(InterestEnum.COOKING),

    TRAVEL(InterestEnum.TRAVEL),
    NATURE_WALKS(InterestEnum.TRAVEL),

    GAMING(InterestEnum.GAMING),
    BOARD_GAMES(InterestEnum.GAMING),

    THEATER(InterestEnum.THEATER),
    STAND_UP_COMEDY(InterestEnum.THEATER),
    STORYTELLING(InterestEnum.THEATER),

    LANGUAGE(InterestEnum.LANGUAGE),
    PUBLIC_SPEAKING(InterestEnum.LANGUAGE),
    WRITING(InterestEnum.LANGUAGE),

    PHOTOGRAPHY(InterestEnum.PHOTOGRAPHY),
    FILM_MAKING(InterestEnum.PHOTOGRAPHY),

    DIY(InterestEnum.DIY),
    CARPENTRY(InterestEnum.DIY),
    INTERIOR_DESIGN(InterestEnum.DIY),

    MAKEUP_BEAUTY(InterestEnum.BEAUTY),

    INVESTMENT_FINANCE(InterestEnum.FINANCE),
    ENTREPRENEURSHIP(InterestEnum.FINANCE),

    PET_TRAINING(InterestEnum.OTHER),
    PODCASTING(InterestEnum.OTHER),
    MAGIC_TRICKS(InterestEnum.OTHER),
    ASTRONOMY(InterestEnum.OTHER),
    PUBLIC_SERVICE(InterestEnum.OTHER),
    PRODUCTIVITY(InterestEnum.OTHER),
    BIRD_WATCHING(InterestEnum.OTHER),
    CULTURAL_EVENTS(InterestEnum.OTHER);

    private final InterestEnum interest;

    ItemTypeEnum(InterestEnum interest) {
        this.interest = interest;
    }

    public InterestEnum getInterest() {
        return interest;
    }

    public String getDisplayName() {
        return Arrays.stream(name().split("_"))
                .map(w -> w.substring(0,1).toUpperCase() + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}
