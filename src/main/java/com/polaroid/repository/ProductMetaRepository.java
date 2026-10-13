package com.polaroid.repository;

import com.polaroid.model.ProductMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductMetaRepository extends JpaRepository<ProductMeta, String> {
}
