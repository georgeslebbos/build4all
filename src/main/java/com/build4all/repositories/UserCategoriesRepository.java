package com.build4all.repositories;

import com.build4all.entities.Category;
import com.build4all.entities.UserCategories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.build4all.entities.UserCategories.UserCategoryId;
import com.build4all.entities.Users;

@Repository
public interface UserCategoriesRepository extends JpaRepository<UserCategories, UserCategoryId> {

	 @Query("SELECT ui.category FROM UserCategories ui WHERE ui.id.user.id = :userId")
	    List<Category> findCategoriesByUserId(@Param("userId") Long userId);

	 List<UserCategories> findByCategoryIdIn(List<Long> myCategoryIds);

	 List<UserCategories> findById_User_Id(Long userId);

	 
	
}

