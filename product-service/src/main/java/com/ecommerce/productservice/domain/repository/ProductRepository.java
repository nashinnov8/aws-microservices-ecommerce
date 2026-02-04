package com.ecommerce.productservice.domain.repository;

import com.ecommerce.productservice.domain.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.awt.print.Pageable;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    @EntityGraph(attributePaths = {"variants", "category", "brand"})
    Optional<Product> findAll(Pageable pageable);
}
