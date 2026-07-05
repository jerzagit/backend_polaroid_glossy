package com.polaroid.service;

import com.polaroid.dto.mapper.PrintSizeMapper;
import com.polaroid.dto.request.PrintSizeRequest;
import com.polaroid.dto.response.PrintSizeResponse;
import com.polaroid.exception.BadRequestException;
import com.polaroid.exception.ResourceNotFoundException;
import com.polaroid.model.PrintSize;
import com.polaroid.repository.PrintSizeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrintSizeService {
    
    private final PrintSizeRepository printSizeRepository;
    private final PrintSizeMapper printSizeMapper;
    
    public List<PrintSizeResponse> getAllPrintSizes() {
        return printSizeRepository.findAll().stream()
                .map(printSizeMapper::toDto)
                .collect(Collectors.toList());
    }
    
    public List<PrintSizeResponse> getActivePrintSizes() {
        return printSizeRepository.findByIsActiveTrue().stream()
                .map(printSizeMapper::toDto)
                .collect(Collectors.toList());
    }
    
    public PrintSizeResponse getPrintSizeById(String id) {
        PrintSize printSize = printSizeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Print size not found: " + id));
        return printSizeMapper.toDto(printSize);
    }
    
    @Transactional
    public PrintSizeResponse createPrintSize(PrintSizeRequest request) {
        String id = request.getId().trim().toLowerCase();
        request.setId(id);
        if (printSizeRepository.existsById(id)) {
            throw new BadRequestException("Print size already exists: " + id);
        }
        
        PrintSize printSize = printSizeMapper.toEntity(request);
        printSize = printSizeRepository.save(printSize);
        return printSizeMapper.toDto(printSize);
    }
    
    @Transactional
    public PrintSizeResponse updatePrintSize(String id, PrintSizeRequest request) {
        String normalizedId = id.trim().toLowerCase();
        PrintSize printSize = printSizeRepository.findById(normalizedId)
                .orElseThrow(() -> new ResourceNotFoundException("Print size not found: " + normalizedId));
        
        printSize.setName(request.getName());
        printSize.setDisplayName(request.getDisplayName());
        printSize.setWidth(request.getWidth());
        printSize.setHeight(request.getHeight());
        printSize.setPrice(request.getPrice());
        printSize.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            printSize.setIsActive(request.getIsActive());
        }
        
        printSize = printSizeRepository.save(printSize);
        return printSizeMapper.toDto(printSize);
    }
    
    @Transactional
    public void deletePrintSize(String id) {
        String normalizedId = id.trim().toLowerCase();
        if (!printSizeRepository.existsById(normalizedId)) {
            throw new ResourceNotFoundException("Print size not found: " + normalizedId);
        }
        printSizeRepository.deleteById(normalizedId);
    }
}
