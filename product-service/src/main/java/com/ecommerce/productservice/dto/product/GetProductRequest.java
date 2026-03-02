package com.ecommerce.productservice.dto.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record GetProductRequest(

        @NotNull
        @Positive
        @Max(100)
        Integer pageNumber,

        @NotNull
        @Positive
        @Max(100)
        Integer pageSize
) {
    public GetProductRequest {
        if (pageNumber == null) pageNumber = 1;
        if (pageSize == null) pageSize = 10;
    }
}
