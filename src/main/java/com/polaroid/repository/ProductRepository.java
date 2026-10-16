package com.polaroid.repository;

import com.polaroid.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByIsActiveTrueOrderBySortOrderAscIdAsc();

    List<Product> findAllByOrderBySortOrderAscIdAsc();

    Optional<Product> findByCode(String code);

    Optional<Product> findByCodeIgnoreCase(String code);

    boolean existsByCode(String code);
}
