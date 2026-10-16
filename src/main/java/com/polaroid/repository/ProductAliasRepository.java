package com.polaroid.repository;

import com.polaroid.model.ProductAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductAliasRepository extends JpaRepository<ProductAlias, String> {
}
