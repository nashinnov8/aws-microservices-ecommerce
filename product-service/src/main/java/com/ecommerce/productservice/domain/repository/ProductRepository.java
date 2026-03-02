package com.ecommerce.productservice.domain.repository;

import com.ecommerce.productservice.domain.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Override
    @EntityGraph(attributePaths = {"variants", "category", "brand"})
    Page<Product> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"variants", "category", "brand"})
    Optional<Product> findById(UUID id);

    boolean existsByBaseSku(String baseSku);
}
