package com.polaroid.repository;

import com.polaroid.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Product p where p.id = :id")
    Optional<Product> findForImageUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    List<Product> findByIsActiveTrueOrderBySortOrderAscIdAsc();

    List<Product> findAllByOrderBySortOrderAscIdAsc();

    Optional<Product> findByCode(String code);

    Optional<Product> findByCodeIgnoreCase(String code);

    boolean existsByCode(String code);
}
