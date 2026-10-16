package com.polaroid.repository;

import com.polaroid.model.ProductPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductPackageRepository extends JpaRepository<ProductPackage, Long> {

    List<ProductPackage> findByVariantIdOrderBySortOrderAscQuantityAsc(Long variantId);

    boolean existsByVariantIdAndQuantity(Long variantId, Integer quantity);
}
