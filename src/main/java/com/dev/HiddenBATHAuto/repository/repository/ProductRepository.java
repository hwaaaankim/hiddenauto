package com.dev.HiddenBATHAuto.repository.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.product.BigSort;
import com.dev.HiddenBATHAuto.model.product.MiddleSort;
import com.dev.HiddenBATHAuto.model.product.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>{

	Product findByName(String name);
	
	List<Product> findAllByBigSort(BigSort bigSort);
	
	Page<Product> findAllByOrderByIdDesc(Pageable pageble);
	
	Page<Product> findAllByBigSort(Pageable pageable, BigSort bigSort);
	
	Page<Product> findAllByMiddleSort(Pageable pageable, MiddleSort bigSort);
	
	Page<Product> findAllByNameContains(Pageable pageble, String name);
	
	Page<Product> findAllByNameContainsOrderByIdDesc(Pageable pageble, String name);
	
	Page<Product> findAllByBigSortAndNameContains(Pageable pageable, BigSort bigSort, String name);
	
	Page<Product> findAllByMiddleSortAndNameContains(Pageable pageable, MiddleSort bigSort, String name);
	
	Page<Product> findAllByBigSortAndNameContainsOrderByIdDesc(Pageable pageable, BigSort bigSort, String name);
	
	Page<Product> findAllByMiddleSortAndNameContainsOrderByIdDesc(Pageable pageable, MiddleSort bigSort, String name);

	Page<Product> findAllByOrderByProductIndexAsc(Pageable pageble);
	
	Page<Product> findAllByBigSortOrderByProductIndexAsc(Pageable pageable, BigSort bigSort);
	
	Page<Product> findAllByMiddleSortOrderByProductIndexAsc(Pageable pageable, MiddleSort bigSort);
	
	Page<Product> findAllBySubjectContainsOrderByProductIndexAsc(Pageable pageble, String subject);
	
	Page<Product> findAllByBigSortAndNameContainsOrderByProductIndexAsc(Pageable pageable, BigSort bigSort, String name);
	
	Page<Product> findAllByMiddleSortAndNameContainsOrderByProductIndexAsc(Pageable pageable, MiddleSort bigSort, String name);

	List<Product> findAllByOrderByProductIndexAsc();
	
	List<Product> findBySubjectContains(String name);
	
	Optional<Product> findByProductCode(String code);
	
	@Query("SELECT MAX(productIndex) FROM Product")
	Optional<Integer> findFirstIndex();
	
	@Query("SELECT p FROM Product p JOIN p.productTags t WHERE t.id IN :tagIds AND p.id <> :productId ORDER BY RAND()")
    List<Product> findRandomProductsByTag(@Param("tagIds") List<Long> tagIds, @Param("productId") Long productId);
	
	@Query("SELECT p FROM Product p " +
           "JOIN p.productTags t " +
           "JOIN p.productColors c " +
           "JOIN p.bigSort bs " +
           "WHERE t.id = :tagId " +
           "AND c.id = :colorId " +
           "AND bs.id = :bigSortId " +
           "ORDER BY p.productIndex ASC")
    Page<Product> findByTagColorAndBigSort(@Param("tagId") Long tagId,
                                           @Param("colorId") Long colorId,
                                           @Param("bigSortId") Long bigSortId,
                                           Pageable pageable);
	
    @Query("SELECT p FROM Product p " +
            "JOIN p.productTags t " +
            "JOIN p.productColors c " +
            "JOIN p.middleSort ms " +
            "JOIN p.bigSort bs " +
            "WHERE t.id = :tagId " +
            "AND c.id = :colorId " +
            "AND ms.id = :middleSortId " +
            "AND bs.id = :bigSortId " +
            "ORDER BY p.productIndex ASC")
     Page<Product> findByTagColorAndSorts(@Param("tagId") Long tagId,
                                          @Param("colorId") Long colorId,
                                          @Param("middleSortId") Long middleSortId,
                                          @Param("bigSortId") Long bigSortId,
                                          Pageable pageable);

    @Query("SELECT p FROM Product p " +
            "JOIN p.productColors c " +
            "JOIN p.middleSort ms " +
            "JOIN p.bigSort bs " +
            "WHERE c.id = :colorId " +
            "AND ms.id = :middleSortId " +
            "AND bs.id = :bigSortId " +
            "ORDER BY p.productIndex ASC")
     Page<Product> findByColorAndSorts(@Param("colorId") Long colorId,
                                       @Param("middleSortId") Long middleSortId,
                                       @Param("bigSortId") Long bigSortId,
                                       Pageable pageable);

    @Query("SELECT p FROM Product p " +
            "JOIN p.productTags t " +
            "JOIN p.middleSort ms " +
            "JOIN p.bigSort bs " +
            "WHERE t.id = :tagId " +
            "AND ms.id = :middleSortId " +
            "AND bs.id = :bigSortId " +
            "ORDER BY p.productIndex ASC")
     Page<Product> findByTagAndSorts(@Param("tagId") Long tagId,
                                     @Param("middleSortId") Long middleSortId,
                                     @Param("bigSortId") Long bigSortId,
                                     Pageable pageable);

    @Query("SELECT p FROM Product p " +
            "JOIN p.middleSort ms " +
            "JOIN p.bigSort bs " +
            "WHERE ms.id = :middleSortId " +
            "AND bs.id = :bigSortId " +
            "ORDER BY p.productIndex ASC")
    Page<Product> findBySorts(@Param("middleSortId") Long middleSortId,
                               @Param("bigSortId") Long bigSortId,
                               Pageable pageable);
	
	@Query("SELECT p FROM Product p " +
	           "JOIN p.productTags t " +
	           "JOIN p.productColors c " +
	           "JOIN p.bigSort bs " +
	           "WHERE t.id = :tagId " +
	           "AND c.id = :colorId " +
	           "AND bs.id = :bigSortId "+
	           "ORDER BY p.productIndex ASC")
	Page<Product> findByTagColorAndBig(@Param("tagId") Long tagId,
                                      @Param("colorId") Long colorId,
                                      @Param("bigSortId") Long bigSortId,
                                      Pageable pageable);
	@Query("SELECT p FROM Product p " +
           "JOIN p.productTags t " +
           "JOIN p.bigSort bs " +
           "WHERE t.id = :tagId " +
           "AND bs.id = :bigSortId " +
           "ORDER BY p.productIndex ASC")
    Page<Product> findByTagAndBigSort(@Param("tagId") Long tagId,
                                      @Param("bigSortId") Long bigSortId,
                                      Pageable pageable);
	
	@Query("SELECT p FROM Product p " +
           "JOIN p.productColors c " +
           "JOIN p.bigSort bs " +
           "WHERE c.id = :colorId " +
           "AND bs.id = :bigSortId " +
           "ORDER BY p.productIndex ASC")
    Page<Product> findByColorAndBigSort(@Param("colorId") Long colorId,
                                        @Param("bigSortId") Long bigSortId,
                                        Pageable pageable);
	
	@Query("SELECT p FROM Product p " +
           "JOIN p.bigSort bs " +
           "WHERE bs.id = :bigSortId " +
           "ORDER BY p.productIndex ASC")
    Page<Product> findByBigSort(@Param("bigSortId") Long bigSortId,
                                Pageable pageable);
	
	// name에 특정 문자열이 포함된 제품을 조회하는 메서드
    List<Product> findByNameContaining(String name);

    // productCode에 특정 문자열이 포함된 제품을 조회하는 메서드
    List<Product> findByProductCodeContaining(String productCode);
	
}



























