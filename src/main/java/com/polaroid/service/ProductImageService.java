package com.polaroid.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.dto.response.ProductListingResponse;
import com.polaroid.exception.BadRequestException;
import com.polaroid.exception.ResourceNotFoundException;
import com.polaroid.model.Product;
import com.polaroid.model.ProductMeta;
import com.polaroid.repository.ProductRepository;
import com.polaroid.repository.ProductMetaRepository;
import com.polaroid.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductImageService {
    private final ProductRepository products;
    private final ProductMetaRepository metadata;
    private final ProductService productService;
    private final StorageService storage;
    private final ObjectMapper mapper;

    @Value("${app.public-api-url:http://localhost:8080}")
    private String publicApiUrl;

    @Transactional
    public ProductListingResponse updateImages(Long id, List<String> images) {
        Product product = findForUpdate(id);
        if (images == null || images.size() > 12) throw new BadRequestException("Use up to 12 images per product");
        List<String> normalized = new ArrayList<>();
        for (String value : images) {
            String url = value == null ? "" : value.trim();
            if (!validUrl(url)) throw new BadRequestException("Images must use HTTPS URLs or existing product image URLs");
            if (normalized.contains(url)) throw new BadRequestException("Duplicate image URLs are not allowed");
            normalized.add(url);
        }
        save(product, normalized);
        return productService.getProductForAdmin(id);
    }

    @Transactional
    public ProductListingResponse upload(Long id, MultipartFile file) {
        Product product = findForUpdate(id);
        List<String> images = new ArrayList<>(images(product));
        if (images.size() >= 12) throw new BadRequestException("Use up to 12 images per product");
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
            throw new BadRequestException("Choose an image up to 10 MB");
        byte[] bytes = safeImage(file);
        String name = UUID.randomUUID() + ".png";
        String key = "catalog/" + id + "/" + name;
        storage.upload(key, bytes, "image/png");
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) storage.delete(key);
                }
            });
        }
        images.add(imageUrl(id, name));
        save(product, images);
        return productService.getProductForAdmin(id);
    }

    @Transactional(readOnly = true)
    public byte[] read(Long id, String name) {
        if (!name.matches("[a-f0-9-]{36}\\.png")) throw new ResourceNotFoundException("Image not found");
        Product product = products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (!images(product).contains(imageUrl(id, name))) throw new ResourceNotFoundException("Image not found");
        byte[] bytes = storage.download("catalog/" + id + "/" + name);
        if (bytes == null || bytes.length == 0) throw new ResourceNotFoundException("Image not found");
        return bytes;
    }

    private byte[] safeImage(MultipartFile file) {
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BadRequestException("Choose a JPEG, PNG or WebP image");
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("jpeg", "jpg", "png", "webp").contains(format))
                    throw new BadRequestException("Choose a JPEG, PNG or WebP image");
                reader.setInput(input);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000)
                    throw new BadRequestException("Image must be no larger than 20 megapixels");
                var image = reader.read(0);
                var output = new ByteArrayOutputStream();
                ImageIO.write(image, "png", output);
                if (output.size() > 20 * 1024 * 1024) throw new BadRequestException("Image is too large after processing");
                return output.toByteArray();
            } finally { reader.dispose(); }
        } catch (IOException ex) {
            throw new BadRequestException("Could not read image. Choose a valid JPEG, PNG or WebP file");
        }
    }

    private boolean validUrl(String value) {
        if (value.length() > 2048) return false;
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null && uri.getUserInfo() == null &&
                    ("https".equalsIgnoreCase(uri.getScheme()) || value.startsWith(publicApiUrl + "/api/products/"));
        } catch (IllegalArgumentException ex) { return false; }
    }

    private String imageUrl(Long id, String name) {
        return publicApiUrl.replaceAll("/$", "") + "/api/products/" + id + "/images/" + name;
    }

    private Product findForUpdate(Long id) {
        return products.findForImageUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private List<String> images(Product product) {
        String json = metadata.findById(product.getCode()).map(ProductMeta::getImages).orElse("[]");
        try { return json == null ? List.of() : mapper.readValue(json, new TypeReference<List<String>>() {}); }
        catch (IOException ex) { throw new IllegalStateException("Invalid stored product images", ex); }
    }

    private void save(Product product, List<String> images) {
        ProductMeta meta = metadata.findById(product.getCode()).orElseGet(() -> ProductMeta.builder().id(product.getCode()).build());
        try { meta.setImages(mapper.writeValueAsString(images)); }
        catch (IOException ex) { throw new IllegalStateException("Could not save product images", ex); }
        metadata.saveAndFlush(meta);
    }
}
