package com.polaroid.repository;

import com.polaroid.model.ProductVariant;
import com.polaroid.model.enums.ProductStyle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProductIdOrderBySortOrderAscIdAsc(Long productId);

    Optional<ProductVariant> findByProductIdAndStyle(Long productId, ProductStyle style);

    boolean existsByProductIdAndStyle(Long productId, ProductStyle style);
}
