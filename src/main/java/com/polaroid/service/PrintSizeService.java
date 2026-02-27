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
        if (printSizeRepository.existsById(request.getId())) {
            throw new BadRequestException("Print size already exists: " + request.getId());
        }
        
        PrintSize printSize = printSizeMapper.toEntity(request);
        printSize = printSizeRepository.save(printSize);
        return printSizeMapper.toDto(printSize);
    }
    
    @Transactional
    public PrintSizeResponse updatePrintSize(String id, PrintSizeRequest request) {
        PrintSize printSize = printSizeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Print size not found: " + id));
        
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
        if (!printSizeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Print size not found: " + id);
        }
        printSizeRepository.deleteById(id);
    }
}
