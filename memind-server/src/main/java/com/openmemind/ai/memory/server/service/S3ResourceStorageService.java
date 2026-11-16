package com.openmemind.ai.memory.server.service;

import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class S3ResourceStorageService implements ResourceStorageService {

    private final S3Client s3Client;
    private final String bucketName;

    public S3ResourceStorageService(
            @Value("${memind.s3.region:us-east-1}") String region,
            @Value("${memind.s3.bucket-name:memind-local-dev-bucket}") String bucketName) {
        this.s3Client = S3Client.builder().region(Region.of(region)).build();
        this.bucketName = bucketName;
    }

    public void uploadResource(String key, InputStream data, long contentLength) {
        PutObjectRequest putObjectRequest =
                PutObjectRequest.builder().bucket(bucketName).key(key).build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(data, contentLength));
    }
}
