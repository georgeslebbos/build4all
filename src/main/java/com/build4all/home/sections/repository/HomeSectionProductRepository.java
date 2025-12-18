package com.build4all.home.sections.repository;

import com.build4all.home.sections.domain.HomeSectionProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * HomeSectionProductRepository
 *
 * Purpose:
 * - Spring Data JPA repository for HomeSectionProduct (the link table between a HomeSection and products).
 * - Provides CRUD operations + derived query methods for:
 *   1) listing products inside a section (ordered)
 *   2) listing only active products inside a section (public view)
 *   3) checking if a product is already linked to a section (avoid duplicates)
 *   4) removing a product link from a section
 *
 * Notes:
 * - We query by section.id because HomeSectionProduct has a ManyToOne relation (section).
 * - Ordering by sortOrder allows the OWNER to control product ordering inside each section.
 * - In the public home endpoint, we usually use the "active true" method to hide inactive links.
 */
public interface HomeSectionProductRepository extends JpaRepository<HomeSectionProduct, Long> {

    /**
     * List ALL product links for a given section (active + inactive), ordered by sortOrder.
     * Useful for OWNER/admin management screens.
     */
    List<HomeSectionProduct> findBySection_IdOrderBySortOrderAsc(Long sectionId);

    /**
     * List ONLY active product links for a given section, ordered by sortOrder.
     * Useful for the public home page response.
     */
    List<HomeSectionProduct> findBySection_IdAndActiveTrueOrderBySortOrderAsc(Long sectionId);

    /**
     * Find an existing link between a section and a specific product.
     * Used to prevent inserting duplicate links (same section + product).
     */
    Optional<HomeSectionProduct> findBySection_IdAndProductId(Long sectionId, Long productId);

    /**
     * Remove the link between a section and a specific product.
     * Used when the OWNER removes a product from a section.
     */
    void deleteBySection_IdAndProductId(Long sectionId, Long productId);
}
