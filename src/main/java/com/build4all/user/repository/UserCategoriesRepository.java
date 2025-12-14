package com.build4all.user.repository;

import com.build4all.catalog.domain.Category;
import com.build4all.user.domain.UserCategories;
import com.build4all.user.domain.UserCategories.UserCategoryId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for the join table/entity UserCategories.
 *
 * UserCategories represents a MANY-TO-MANY relation:
 *   Users  <->  Category
 *
 * In your code, it is modeled as an entity with a composite key (UserCategoryId),
 * instead of using @ManyToMany directly.
 *
 * Why the key type is UserCategoryId?
 * - Because UserCategories uses @EmbeddedId (composite PK) containing:
 *   - user (Users)
 *   - category (Category)
 */
@Repository
public interface UserCategoriesRepository extends JpaRepository<UserCategories, UserCategoryId> {

    /**
     * Returns ONLY the Category objects for a given user id.
     *
     * JPQL: "SELECT ui.category FROM UserCategories ui WHERE ui.id.user.id = :userId"
     *
     * Explanation:
     * - ui is a row in the join entity UserCategories
     * - ui.id.user.id navigates into the embedded key:
     *      ui.id           -> UserCategoryId
     *      ui.id.user      -> Users entity inside the key
     *      ui.id.user.id   -> user_id column
     * - ui.category is the Category entity linked to that row
     *
     * Result: List<Category> (not List<UserCategories>)
     */
    @Query("SELECT ui.category FROM UserCategories ui WHERE ui.id.user.id = :userId")
    List<Category> findCategoriesByUserId(@Param("userId") Long userId);

    /**
     * Returns all UserCategories rows where the Category id is in (categoryIds).
     *
     * Derived query:
     * - category is a field in UserCategories
     * - category.id is the PK of Category
     * - "In" generates SQL: WHERE category_id IN (...)
     *
     * Useful when you want the join rows (so you can also know the user, timestamps, etc.)
     * not just Category list.
     */
    List<UserCategories> findByCategory_IdIn(List<Long> categoryIds);

    /**
     * Returns all UserCategories rows for a given user id.
     *
     * Derived query navigating embedded id:
     * - id = UserCategoryId
     * - id.user = Users
     * - id.user.id = user_id
     *
     * SQL equivalent:
     *   SELECT * FROM user_categories WHERE user_id = ?
     */
    List<UserCategories> findById_User_Id(Long userId);
}
