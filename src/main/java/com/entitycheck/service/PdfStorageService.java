package com.entitycheck.service;

import com.entitycheck.model.GeneratedDocument;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;

@Service
public class PdfStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucketName;

    @Value("${app.uploads.dir}")
    private String uploadDir;

    public PdfStorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @PostConstruct
    public void init() {
        try {
            boolean found = minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(io.minio.MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize MinIO bucket", e);
        }
    }

    public String save(byte[] pdfBytes, String fileName) {
        try {
            String objectName = uploadDir + "/" + fileName;
            try (ByteArrayInputStream bais = new ByteArrayInputStream(pdfBytes)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .stream(bais, pdfBytes.length, -1)
                                .contentType("application/pdf")
                                .build()
                );
            }
            return objectName;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to save PDF to MinIO", ex);
        }
    }

    public byte[] read(GeneratedDocument doc) {
        try {
            if (doc.getFilePath() != null && !doc.getFilePath().isBlank()) {
                String objectName = doc.getFilePath();
                try (InputStream is = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .build()
                )) {
                    return is.readAllBytes();
                }
            }
            if (doc.getPdfBase64() != null && !doc.getPdfBase64().isBlank()) {
                return Base64.getDecoder().decode(doc.getPdfBase64());
            }
            throw new RuntimeException("PDF content not found");
        } catch (Exception ex) {
            throw new RuntimeException("Failed to read PDF from MinIO", ex);
        }
    }
}
