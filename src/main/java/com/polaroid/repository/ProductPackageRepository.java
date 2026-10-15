package com.polaroid.repository;

import com.polaroid.model.ProductPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductPackageRepository extends JpaRepository<ProductPackage, String> {
    List<ProductPackage> findByVariantIdOrderBySortOrderAsc(String variantId);
    boolean existsByVariantIdAndQuantity(String variantId, Integer quantity);
}
