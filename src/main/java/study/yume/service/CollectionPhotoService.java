package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionPhotoService {
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final GoshuinService goshuin;
    private final CollectionPhotoStorage storage;
    private final CollectionImageProcessor images;
    private final Semaphore slots=new Semaphore(2);
    public record FileContent(byte[] bytes,String type) {}
    private String table(boolean book){return book?"goshuin_book":"spot_purchase";}
    private String versionColumn(boolean book){return book?"version":"record_version";}
    private String currentPhoto(Long userId,Long id,boolean book,Long version) {
        if(book){var b=goshuin.book(userId,id);GoshuinService.checkVersion(version,b.version());return b.photoId();}
        var e=goshuin.entry(userId,id);GoshuinService.checkVersion(version,e.version());return e.photoId();
    }
    public String upload(Long userId,Long target,boolean book,Long version,MultipartFile file) throws IOException {
        if(!slots.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"사진 처리 중입니다. 잠시 후 다시 올려 주세요.");
        String photo=UUID.randomUUID().toString(),prefix=userId+"/"+photo;
        try {
            transactions.executeWithoutResult(tx->currentPhoto(userId,target,book,version));
            if(file.getSize()>10*1024*1024) throw new IllegalArgumentException("사진은 10MB 이하로 선택해 주세요.");
            var image=images.process(file.getBytes());
            transactions.executeWithoutResult(tx->db.update("""
                INSERT INTO collection_photo(id,user_id,storage,original_key,thumbnail_key,content_type,width,height,byte_size,state)
                VALUES (?,?,?,?,?,?,?,?,?,'PENDING')
                """,photo,userId,storage.provider(),prefix+"/original",prefix+"/thumbnail.jpg",image.contentType(),image.width(),image.height(),image.original().length));
            try {
                storage.put(prefix+"/original",image.original(),image.contentType());
                storage.put(prefix+"/thumbnail.jpg",image.thumbnail(),"image/jpeg");
                transactions.executeWithoutResult(tx->{
                    goshuin.lockOwner(userId);
                    String old=currentPhoto(userId,target,book,version);
                    if(db.update("UPDATE collection_photo SET state='READY' WHERE id=? AND state='PENDING'",photo)!=1)
                        throw new IllegalStateException("사진 처리 상태가 변경됐습니다. 다시 올려 주세요.");
                    db.update("UPDATE "+table(book)+" SET photo_id=?,"+versionColumn(book)+"="+versionColumn(book)+"+1 WHERE user_id=? AND id=?",photo,userId,target);
                    goshuin.retirePhoto(old);
                    if(!book)goshuin.touchBook(userId,goshuin.entry(userId,target).bookId());
                });
                return photo;
            } catch(IOException | RuntimeException error) {
                transactions.executeWithoutResult(tx->goshuin.retirePhoto(photo));throw error;
            }
        } finally {slots.release();}
    }
    public void remove(Long userId,Long target,boolean book,Long version) {
        transactions.executeWithoutResult(tx->{
            goshuin.lockOwner(userId);String old=currentPhoto(userId,target,book,version);
            db.update("UPDATE "+table(book)+" SET photo_id=NULL,"+versionColumn(book)+"="+versionColumn(book)+"+1 WHERE user_id=? AND id=?",userId,target);
            goshuin.retirePhoto(old);
            if(!book)goshuin.touchBook(userId,goshuin.entry(userId,target).bookId());
        });
    }
    public FileContent get(Long userId,String id,boolean thumbnail) throws IOException {
        // Transaction ends before local/S3 IO. Only attached READY photos are served.
        var rows=transactions.execute(tx->db.queryForList("""
            SELECT a.* FROM collection_photo a WHERE a.id=? AND a.user_id=? AND a.state='READY'
            AND (EXISTS (SELECT 1 FROM spot_purchase p WHERE p.photo_id=a.id AND p.user_id=a.user_id)
             OR EXISTS (SELECT 1 FROM goshuin_book b WHERE b.photo_id=a.id AND b.user_id=a.user_id))
            """,id,userId));
        if(rows==null || rows.isEmpty())throw new EntityNotFoundException("사진을 찾을 수 없습니다.");
        var a=rows.get(0);
        return new FileContent(storage.get((String)a.get("storage"),(String)a.get(thumbnail?"thumbnail_key":"original_key")),thumbnail?"image/jpeg":(String)a.get("content_type"));
    }
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void cleanup() {
        var rows=transactions.execute(tx->db.queryForList("""
            SELECT * FROM collection_photo WHERE (cleanup_after IS NULL OR cleanup_after <= CURRENT_TIMESTAMP)
             AND (state='DELETE' OR (state='PENDING' AND created_at < ?)) ORDER BY created_at LIMIT 20
            """,java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(86400))));
        if(rows==null)return;
        for(var row:rows)try {
            String id=(String)row.get("id");
            storage.delete((String)row.get("storage"),(String)row.get("original_key"));
            storage.delete((String)row.get("storage"),(String)row.get("thumbnail_key"));
            transactions.executeWithoutResult(tx->db.update("DELETE FROM collection_photo WHERE id=? AND state IN ('DELETE','PENDING')",id));
        }catch(Exception error){
            transactions.executeWithoutResult(tx->db.update("UPDATE collection_photo SET cleanup_after=? WHERE id=?",
                java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(300)),row.get("id")));
            log.warn("collection_photo_cleanup_failed id={}",row.get("id"),error);
        }
    }
}
