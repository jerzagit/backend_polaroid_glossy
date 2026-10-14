package com.polaroid.repository;

import com.polaroid.model.PrintSizePricingTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrintSizePricingTierRepository extends JpaRepository<PrintSizePricingTier, String> {
    List<PrintSizePricingTier> findBySizeIdOrderByQuantityAsc(String sizeId);
}
