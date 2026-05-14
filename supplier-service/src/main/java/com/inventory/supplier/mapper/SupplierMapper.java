package com.inventory.supplier.mapper;

import com.inventory.supplier.dto.response.SupplierResponse;
import com.inventory.supplier.entity.Supplier;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SupplierMapper {
    SupplierResponse toResponse(Supplier supplier);
}