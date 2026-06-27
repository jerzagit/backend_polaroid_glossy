package com.polaroid.service;

import com.polaroid.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileServiceSecurityTest {

    @TempDir
    Path tempDir;

    @Test
    void validateAndProcessImage_validJpeg() throws Exception {
        byte[] jpegBytes = createJpeg(800, 600);
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes);
        byte[] processed = processImage(file);

        assertNotNull(processed);
        assertTrue(processed.length > 0);

        BufferedImage result = ImageIO.read(new java.io.ByteArrayInputStream(processed));
        assertNotNull(result);
        assertEquals(800, result.getWidth());
        assertEquals(600, result.getHeight());
    }

    @Test
    void validateAndProcessImage_validPng() throws Exception {
        BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        byte[] pngBytes = baos.toByteArray();

        MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", pngBytes);
        byte[] processed = processImage(file);

        assertNotNull(processed);
        BufferedImage result = ImageIO.read(new java.io.ByteArrayInputStream(processed));
        assertNotNull(result);
        assertEquals(400, result.getWidth());
        assertEquals(300, result.getHeight());
    }

    @Test
    void validateAndProcessImage_wrongMagicBytes_rejected() {
        byte[] fakeImage = "this is not an image".getBytes();
        MultipartFile file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", fakeImage);
        BadRequestException ex = assertThrows(BadRequestException.class, () -> processImage(file));
        assertTrue(ex.getMessage().toLowerCase().contains("magic bytes"));
    }

    @Test
    void validateAndProcessImage_spoofedContentType_accepted() throws Exception {
        byte[] jpegBytes = createJpeg(100, 100);
        MultipartFile file = new MockMultipartFile("file", "virus.exe", "image/jpeg", jpegBytes);
        byte[] processed = processImage(file);
        assertNotNull(processed);
    }

    @Test
    void validateAndProcessImage_exeWithJpegContentType_rejected() {
        byte[] exeBytes = new byte[]{
                (byte) 0x4D, (byte) 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00,
                0x04, 0x00, 0x00, 0x00
        };
        MultipartFile file = new MockMultipartFile("file", "malware.exe", "image/jpeg", exeBytes);
        BadRequestException ex = assertThrows(BadRequestException.class, () -> processImage(file));
        assertTrue(ex.getMessage().toLowerCase().contains("magic bytes"));
    }

    @Test
    void validateAndProcessImage_decompressionBomb_rejected() throws Exception {
        byte[] jpegBytes = createJpeg(6000, 6000);
        MultipartFile file = new MockMultipartFile("file", "bomb.jpg", "image/jpeg", jpegBytes);
        BadRequestException ex = assertThrows(BadRequestException.class, () -> processImage(file));
        assertTrue(ex.getMessage().toLowerCase().contains("decompression"));
    }

    @Test
    void validateAndProcessImage_exceedsMaxDimension_rejected() throws Exception {
        byte[] jpegBytes = createJpeg(15000, 100);
        MultipartFile file = new MockMultipartFile("file", "wide.jpg", "image/jpeg", jpegBytes);
        BadRequestException ex = assertThrows(BadRequestException.class, () -> processImage(file));
        assertTrue(ex.getMessage().toLowerCase().contains("dimensions"));
    }

    @Test
    void validateAndProcessImage_emptyFile_rejected() {
        MultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
        BadRequestException ex = assertThrows(BadRequestException.class, () -> processImage(file));
        assertTrue(ex.getMessage().toLowerCase().contains("required"));
    }

    @Test
    void validateAndProcessImage_bogusPngWithValidHeader_rejected() {
        byte[] pngHeader = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        };
        byte[] garbage = new byte[1000];
        byte[] fakePng = new byte[pngHeader.length + garbage.length];
        System.arraycopy(pngHeader, 0, fakePng, 0, pngHeader.length);
        System.arraycopy(garbage, 0, fakePng, pngHeader.length, garbage.length);

        MultipartFile file = new MockMultipartFile("file", "fake.png", "image/png", fakePng);
        BadRequestException ex = assertThrows(BadRequestException.class, () -> processImage(file));
        assertTrue(ex.getMessage().toLowerCase().contains("decode"));
    }

    @Test
    void detectFormat_jpegHeader() throws Exception {
        byte[] jpegBytes = createJpeg(100, 100);
        String format = detectFormat(jpegBytes);
        assertEquals("jpeg", format);
    }

    @Test
    void detectFormat_pngHeader() throws Exception {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        String format = detectFormat(baos.toByteArray());
        assertEquals("png", format);
    }

    @Test
    void detectFormat_invalid_returnsNull() {
        assertNull(detectFormat("garbage".getBytes()));
        assertNull(detectFormat(new byte[0]));
    }

    private byte[] processImage(MultipartFile file) {
        try {
            FileService service = new FileService(null, null, null, null);
            Method method = FileService.class.getDeclaredMethod("validateAndProcessImage", MultipartFile.class);
            method.setAccessible(true);
            return (byte[]) method.invoke(service, file);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e.getCause());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String detectFormat(byte[] fileBytes) {
        try {
            FileService service = new FileService(null, null, null, null);
            Method method = FileService.class.getDeclaredMethod("detectImageFormat", byte[].class);
            method.setAccessible(true);
            return (String) method.invoke(service, fileBytes);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e.getCause());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] createJpeg(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }
}
