package study.yume.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;

@Component
public class CollectionPhotoStorage {
    private final Path root;
    private final String provider,bucket,region;
    private S3Client s3;
    public CollectionPhotoStorage(
        @Value("${collection.photos.provider:local}") String provider,
        @Value("${collection.photos.local-directory:./uploads/collection}") String directory,
        @Value("${collection.photos.s3-bucket:}") String bucket,
        @Value("${collection.photos.s3-region:ap-northeast-2}") String region) {
        if(!provider.equals("local") && !provider.equals("s3")) throw new IllegalArgumentException("사진 저장소는 local 또는 s3여야 합니다.");
        if(provider.equals("s3") && bucket.isBlank()) throw new IllegalArgumentException("S3 사진 버킷을 설정해 주세요.");
        this.root=Path.of(directory).toAbsolutePath().normalize();this.provider=provider;this.bucket=bucket;this.region=region;
    }
    public String provider(){return provider;}
    private synchronized S3Client s3() {
        if(s3==null) s3=S3Client.builder().region(Region.of(region)).overrideConfiguration(c->
            c.apiCallTimeout(Duration.ofSeconds(30)).apiCallAttemptTimeout(Duration.ofSeconds(15))).build();
        return s3;
    }
    private Path path(String key) {
        Path path=root.resolve(key).normalize();
        if(!path.startsWith(root)) throw new IllegalArgumentException("잘못된 사진 경로입니다.");
        return path;
    }
    public void put(String key,byte[] content,String type) throws IOException {
        if(provider.equals("s3")) s3().putObject(r->r.bucket(bucket).key(key).contentType(type),RequestBody.fromBytes(content));
        else {Path file=path(key);Files.createDirectories(file.getParent());Files.write(file,content,StandardOpenOption.CREATE_NEW);}
    }
    public byte[] get(String storage,String key) throws IOException {
        if(storage.equals("s3")) return s3().getObjectAsBytes(r->r.bucket(bucket).key(key)).asByteArray();
        return Files.readAllBytes(path(key));
    }
    public void delete(String storage,String key) throws IOException {
        if(storage.equals("s3")) s3().deleteObject(r->r.bucket(bucket).key(key));
        else Files.deleteIfExists(path(key));
    }
    @jakarta.annotation.PreDestroy public void close(){if(s3!=null)s3.close();}
}
