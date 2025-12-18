package com.build4all.home.sections.domain;

/**
 * HomeSectionLayout
 *
 * Purpose:
 * - Defines how a HomeSection should be rendered on the frontend.
 * - This value is stored in the DB as a STRING (see HomeSection.layout) and sent to the client.
 *
 * Usage in UI:
 * - HORIZONTAL: show products in a horizontal carousel/slider (one row, scroll left/right).
 * - GRID: show products in a grid layout (e.g., 2 columns, scroll vertically with the page).
 *
 * Notes:
 * - Keep these enum values stable because the frontend may depend on them.
 * - If you add new layouts later (e.g., BANNER_STRIP, LIST, HERO), update both backend and frontend.
 */
public enum HomeSectionLayout {
    HORIZONTAL,
    GRID
}
