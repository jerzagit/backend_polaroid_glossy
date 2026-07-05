package com.polaroid.service;

import com.polaroid.dto.request.AddressRequest;
import com.polaroid.dto.response.AddressResponse;
import com.polaroid.exception.BadRequestException;
import com.polaroid.exception.ResourceNotFoundException;
import com.polaroid.model.Address;
import com.polaroid.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AddressService {

    private static final int MAX_ADDRESSES = 10;

    private final AddressRepository addressRepository;

    public List<AddressResponse> getAddresses(UUID userId) {
        return addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public AddressResponse createAddress(UUID userId, AddressRequest request) {
        long count = addressRepository.countByUserId(userId);
        if (count >= MAX_ADDRESSES) {
            throw new BadRequestException("Maximum of " + MAX_ADDRESSES + " addresses allowed");
        }

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefaultFlag(userId);
        }

        boolean hasExisting = count > 0;

        Address address = Address.builder()
                .userId(userId)
                .label(request.getLabel() != null ? request.getLabel() : "Other")
                .name(request.getName())
                .phone(request.getPhone())
                .houseUnitNo(request.getHouseUnitNo())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .state(request.getState())
                .postalCode(request.getPostalCode())
                .country(request.getCountry() != null ? request.getCountry() : "Malaysia")
                .isDefault(request.getIsDefault() != null ? request.getIsDefault() : !hasExisting)
                .build();

        return toDto(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse updateAddress(UUID addressId, UUID userId, AddressRequest request) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (!address.getUserId().equals(userId)) {
            throw new BadRequestException("Not authorized to update this address");
        }

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefaultFlag(userId);
        }

        if (request.getLabel() != null) address.setLabel(request.getLabel());
        if (request.getName() != null) address.setName(request.getName());
        if (request.getPhone() != null) address.setPhone(request.getPhone());
        if (request.getHouseUnitNo() != null) address.setHouseUnitNo(request.getHouseUnitNo());
        if (request.getAddressLine1() != null) address.setAddressLine1(request.getAddressLine1());
        if (request.getAddressLine2() != null) address.setAddressLine2(request.getAddressLine2());
        if (request.getCity() != null) address.setCity(request.getCity());
        if (request.getState() != null) address.setState(request.getState());
        if (request.getPostalCode() != null) address.setPostalCode(request.getPostalCode());
        if (request.getCountry() != null) address.setCountry(request.getCountry());
        if (request.getIsDefault() != null) address.setIsDefault(request.getIsDefault());

        return toDto(addressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(UUID addressId, UUID userId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (!address.getUserId().equals(userId)) {
            throw new BadRequestException("Not authorized to delete this address");
        }

        addressRepository.delete(address);
    }

    @Transactional
    public AddressResponse setDefault(UUID addressId, UUID userId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (!address.getUserId().equals(userId)) {
            throw new BadRequestException("Not authorized to modify this address");
        }

        clearDefaultFlag(userId);
        address.setIsDefault(true);

        return toDto(addressRepository.save(address));
    }

    private void clearDefaultFlag(UUID userId) {
        List<Address> userAddresses = addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
        for (Address addr : userAddresses) {
            if (Boolean.TRUE.equals(addr.getIsDefault())) {
                addr.setIsDefault(false);
                addressRepository.save(addr);
            }
        }
    }

    private AddressResponse toDto(Address address) {
        return AddressResponse.builder()
                .id(address.getId().toString())
                .label(address.getLabel())
                .name(address.getName())
                .phone(address.getPhone())
                .houseUnitNo(address.getHouseUnitNo())
                .addressLine1(address.getAddressLine1())
                .addressLine2(address.getAddressLine2())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .isDefault(address.getIsDefault())
                .createdAt(address.getCreatedAt())
                .build();
    }
}
