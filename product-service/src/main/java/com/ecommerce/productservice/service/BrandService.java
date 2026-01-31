package com.ecommerce.productservice.service;

import com.ecommerce.productservice.domain.entity.Brand;
import com.ecommerce.productservice.domain.repository.BrandRepository;
import com.ecommerce.productservice.dto.brand.BrandRequest;
import com.ecommerce.productservice.dto.brand.BrandResponse;
import com.ecommerce.productservice.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BrandService {
    private final BrandRepository brandRepository;

    public BrandService(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    @Transactional
    public BrandResponse create(BrandRequest request) {
        Brand brand = new Brand();
        brand.setName(request.name());
        brand.setDescription(request.description());
        brand.setLogoUrl(request.logoUrl());
        brand.setWebsiteUrl(request.websiteUrl());
        brand.setIsActive(true);

        Brand savedBrand = brandRepository.save(brand);
        return BrandResponse.fromEntity(savedBrand);
    }

    @Transactional(readOnly = true)
    public List<BrandResponse> getAll() {
        return brandRepository.findAll().stream()
                .map(BrandResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public BrandResponse getById(UUID id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + id));
        return BrandResponse.fromEntity(brand);
    }

    @Transactional
    public BrandResponse update(UUID id, BrandRequest request) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + id));

        brand.setName(request.name());
        brand.setDescription(request.description());
        brand.setLogoUrl(request.logoUrl());
        brand.setWebsiteUrl(request.websiteUrl());

        Brand updatedBrand = brandRepository.save(brand);
        return BrandResponse.fromEntity(updatedBrand);
    }

    @Transactional
    public void delete(UUID id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + id));
        brandRepository.delete(brand);
    }
}
