package com.build4all.cart.repository;

import com.build4all.cart.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * CartItemRepository
 *
 * Spring Data JPA repository for CartItem entity.
 * - Inherits CRUD operations (save, findById, findAll, delete, ...)
 * - Adds a custom delete operation to clear all items belonging to a cart.
 */
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /**
     * Delete all CartItem rows for a given cart id.
     *
     * Why is this useful?
     * - During checkout: once an Order is created, we want to clear the cart quickly.
     * - Instead of loading all CartItem entities into memory and deleting one-by-one,
     *   this issues a single bulk delete statement like:
     *   DELETE FROM cart_items WHERE cart_id = ?
     *
     * Notes:
     * - This is a derived query method: Spring Data parses the name "deleteByCart_Id"
     *   and generates the delete query automatically.
     * - Bulk operations bypass the persistence context for affected entities, so if you
     *   have Cart.items already loaded in memory, it’s good practice to also clear that list
     *   (as you do in checkout: cart.getItems().clear()) to keep the in-memory state consistent.
     *
     * @param cartId the cart id whose items should be deleted
     */
    void deleteByCart_Id(Long cartId);
}
