package com.build4all.user.repository;

import com.build4all.catalog.domain.Category;
import com.build4all.user.domain.UserCategories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.build4all.user.domain.UserCategories.UserCategoryId;

@Repository
public interface UserCategoriesRepository extends JpaRepository<UserCategories, UserCategoryId> {

	 @Query("SELECT ui.category FROM UserCategories ui WHERE ui.id.user.id = :userId")
	    List<Category> findCategoriesByUserId(@Param("userId") Long userId);

	List<UserCategories> findByCategory_IdIn(List<Long> categoryIds);

	 List<UserCategories> findById_User_Id(Long userId);

	 
	
}

