package study.yume.service;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mock.web.MockMultipartFile;
import study.yume.dto.goshuin.GoshuinDto.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(GoshuinServiceTests.Config.class)
class GoshuinServiceTests {
    @Configuration @EnableTransactionManagement static class Config {
        @Bean(destroyMethod="close") HikariDataSource dataSource(){
            var ds=new HikariDataSource();ds.setJdbcUrl("jdbc:h2:mem:goshuin;MODE=MySQL;DB_CLOSE_DELAY=-1");ds.setMaximumPoolSize(2);
            new ResourceDatabasePopulator(new ClassPathResource("goshuin-fixture.sql")).execute(ds);return ds;
        }
        @Bean JdbcTemplate jdbc(HikariDataSource ds){return new JdbcTemplate(ds);}
        @Bean DataSourceTransactionManager manager(HikariDataSource ds){return new DataSourceTransactionManager(ds);}
        @Bean TransactionTemplate transactions(DataSourceTransactionManager manager){return new TransactionTemplate(manager);}
        @Bean GoshuinService service(JdbcTemplate db){return new GoshuinService(db);}
        @Bean CollectionPhotoStorage storage(){return mock(CollectionPhotoStorage.class);}
        @Bean CollectionImageProcessor processor(){return new CollectionImageProcessor();}
        @Bean CollectionPhotoService photos(JdbcTemplate db,TransactionTemplate tx,GoshuinService g,CollectionPhotoStorage s,CollectionImageProcessor i){return new CollectionPhotoService(db,tx,g,s,i);}
    }
    @Autowired GoshuinService service;
    @Autowired JdbcTemplate db;
    @Autowired HikariDataSource pool;
    @Autowired CollectionPhotoStorage storage;
    @Autowired CollectionPhotoService photos;
    @Autowired CollectionImageProcessor images;
    @BeforeEach void resetData(){
        db.update("DELETE FROM collection_photo");db.update("UPDATE spot_purchase SET goshuin_book_id=NULL");
        db.update("DELETE FROM goshuin_book");db.update("DELETE FROM spot_purchase");
        db.update("DELETE FROM spot_user");db.update("DELETE FROM spot");db.update("DELETE FROM users");
        db.update("INSERT INTO users(id) VALUES (1),(2)");reset(storage);when(storage.provider()).thenReturn("local");
    }
    Book book(String name){return service.saveBook(1L,null,new BookInput(name,null,null,null));}
    Entry add(String place,Long book,Integer position){return service.saveEntry(1L,null,new EntryInput(place,null,null,null,null,book,position,null));}
    EntryInput input(Entry e,Long book,Integer pos){return new EntryInput(e.placeName(),e.spotUserId(),e.itemName(),e.acquiredDate(),e.note(),book,pos,e.version());}
    @Test void insertMoveDetachAndDateMaintainContinuousOrder(){
        Book b=book("첩");Entry a=add("A",b.id(),null),c=add("C",b.id(),null),middle=add("B",b.id(),2);
        assertEquals(List.of("A","B","C"),service.order(1L,b.id()).stream().map(Entry::placeName).toList());
        assertEquals(List.of(1,2,3),service.order(1L,b.id()).stream().map(Entry::position).toList());
        Entry current=service.entry(1L,middle.id());
        service.saveEntry(1L,current.id(),new EntryInput(current.placeName(),null,null,LocalDate.of(2020,1,1),null,b.id(),null,current.version()));
        assertEquals(List.of(a.id(),middle.id(),c.id()),service.order(1L,b.id()).stream().map(Entry::id).toList());
        Book other=book("다른 첩");current=service.entry(1L,middle.id());
        Entry moved=service.saveEntry(1L,current.id(),input(current,other.id(),null));assertEquals(1,moved.position());
        service.saveEntry(1L,moved.id(),input(moved,null,null));
        assertNull(service.entry(1L,moved.id()).position());assertEquals(2,service.book(1L,b.id()).count());
    }
    @Test void defaultBookDeletionPreservesRecordsAndOnlyRetiresCover(){
        Book b=book("첩");Entry e=add("직접 입력 장소",b.id(),null),loose=add("종이 고슈인",null,null);
        asset("cover");asset("stamp");
        db.update("UPDATE goshuin_book SET photo_id='cover' WHERE id=?",b.id());
        db.update("UPDATE spot_purchase SET photo_id='stamp' WHERE id=?",e.id());
        service.deleteBook(1L,b.id(),false,service.book(1L,b.id()).version());
        Entry saved=service.entry(1L,e.id());assertNull(saved.bookId());assertNull(saved.position());assertNull(saved.acquiredDate());assertEquals("stamp",saved.photoId());
        assertEquals("직접 입력 장소",saved.placeName());assertNotNull(service.entry(1L,loose.id()));
        assertEquals("DELETE",db.queryForObject("SELECT state FROM collection_photo WHERE id='cover'",String.class));
        assertEquals("READY",db.queryForObject("SELECT state FROM collection_photo WHERE id='stamp'",String.class));
    }
    @Test void togetherDeletionOnlyDeletesCurrentBooksEntries(){
        Book b=book("지울 첩"),other=book("보존");Entry own=add("A",b.id(),null),saved=add("B",other.id(),null),loose=add("종이",null,null);
        asset("stamp");db.update("UPDATE spot_purchase SET photo_id='stamp' WHERE id=?",own.id());
        service.deleteBook(1L,b.id(),true,service.book(1L,b.id()).version());
        assertThrows(EntityNotFoundException.class,()->service.entry(1L,own.id()));
        assertNotNull(service.entry(1L,saved.id()));assertNotNull(service.entry(1L,loose.id()));
        assertEquals("DELETE",db.queryForObject("SELECT state FROM collection_photo WHERE id='stamp'",String.class));
    }
    @Test void staleVersionRejectsReorderAndDeletionWithoutDataLoss(){
        Book b=book("첩");Entry a=add("A",b.id(),null);Book before=service.book(1L,b.id());Entry c=add("C",b.id(),null);
        assertThrows(ResponseStatusException.class,()->service.reorder(1L,b.id(),new OrderInput(List.of(c.id(),a.id()),before.version())));
        assertThrows(ResponseStatusException.class,()->service.deleteBook(1L,b.id(),true,before.version()));
        assertEquals(2,service.book(1L,b.id()).count());
        Book latest=service.book(1L,b.id());
        assertThrows(IllegalArgumentException.class,()->service.reorder(1L,b.id(),new OrderInput(List.of(a.id(),a.id()),latest.version())));
    }
    @Test void ownersCannotReadLinkOrDeleteEachOthersCollection(){
        Book b=book("개인");Entry e=add("개인 장소",b.id(),null);
        assertThrows(EntityNotFoundException.class,()->service.book(2L,b.id()));
        assertThrows(EntityNotFoundException.class,()->service.entry(2L,e.id()));
        assertThrows(EntityNotFoundException.class,()->service.deleteBook(2L,b.id(),true,service.book(1L,b.id()).version()));
        assertThrows(EntityNotFoundException.class,()->service.saveEntry(2L,null,new EntryInput("장소",null,null,null,null,b.id(),null,null)));
    }
    @Test void importLinksSameRecordAndRollsBackInvalidBatch(){
        Book b=book("첩");Entry a=add("A",null,null),c=add("C",null,null);
        db.update("UPDATE spot_purchase SET price=500,quantity=2,currency='JPY' WHERE id=?",a.id());
        assertThrows(EntityNotFoundException.class,()->service.importEntries(1L,b.id(),new ImportInput(List.of(a.id(),999999L),b.version(),Map.of(a.id(),a.version(),999999L,0L))));
        assertNull(service.entry(1L,a.id()).bookId());
        service.importEntries(1L,b.id(),new ImportInput(List.of(c.id(),a.id()),b.version(),Map.of(c.id(),c.version(),a.id(),a.version())));
        assertEquals(List.of(c.id(),a.id()),service.order(1L,b.id()).stream().map(Entry::id).toList());
        assertEquals(2,service.entry(1L,a.id()).quantity());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM spot_purchase",Integer.class));
        assertEquals(500,db.queryForObject("SELECT price FROM spot_purchase WHERE id=?",Integer.class,a.id()));
    }
    @Test void invalidInsertionRollsBackTheEntireMove(){
        Book first=book("첫 첩"),second=book("두 번째");Entry a=add("A",first.id(),null);
        assertThrows(IllegalArgumentException.class,()->service.saveEntry(1L,a.id(),input(a,second.id(),99)));
        assertEquals(first.id(),service.entry(1L,a.id()).bookId());assertEquals(1,service.entry(1L,a.id()).position());
    }
    @Test void deletingLinkedBookKeepsPurchaseAndDeletingPurchaseUnlinksBook(){
        db.update("INSERT INTO spot_purchase(user_id,kind,status,quantity,item_name) VALUES (1,'GOSHUINCHO','ACQUIRED',1,'실물 첩')");
        Long source=db.queryForObject("SELECT id FROM spot_purchase WHERE kind='GOSHUINCHO'",Long.class);
        Book b=service.saveBook(1L,null,new BookInput("첩",null,source,null));
        assertEquals(b.id(),service.saveBook(1L,null,new BookInput("중복 생성",null,source,null)).id());
        service.deleteBook(1L,b.id(),true,b.version());assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM spot_purchase",Integer.class));
        b=service.saveBook(1L,null,new BookInput("다시 첩",null,source,null));db.update("DELETE FROM spot_purchase WHERE id=?",source);
        assertNull(service.book(1L,b.id()).sourcePurchaseId());
    }
    @Test void uploadAndCleanupDoNotHoldDatabaseConnectionsDuringStorageIO() throws Exception {
        Entry e=add("A",null,null);byte[] data=png(900,450);
        doAnswer(call->{assertEquals(0,pool.getHikariPoolMXBean().getActiveConnections());return null;}).when(storage).put(anyString(),any(),anyString());
        String id=photos.upload(1L,e.id(),false,e.version(),new MockMultipartFile("file","photo.png","image/png",data));
        assertEquals(id,service.entry(1L,e.id()).photoId());
        assertThrows(EntityNotFoundException.class,()->photos.get(2L,id,true));
        Entry latest=service.entry(1L,e.id());photos.remove(1L,latest.id(),false,latest.version());
        doAnswer(call->{assertEquals(0,pool.getHikariPoolMXBean().getActiveConnections());return null;}).when(storage).delete(anyString(),anyString());
        photos.cleanup();assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM collection_photo",Integer.class));
    }
    @Test void failedReplacementKeepsOldPhotoAndQueuesPartialFiles() throws Exception {
        Entry e=add("A",null,null);asset("old");db.update("UPDATE spot_purchase SET photo_id='old' WHERE id=?",e.id());
        doThrow(new java.io.IOException("storage failure")).when(storage).put(endsWith("thumbnail.jpg"),any(),anyString());
        assertThrows(java.io.IOException.class,()->photos.upload(1L,e.id(),false,e.version(),new MockMultipartFile("file","a.png","image/png",png(100,200))));
        assertEquals("old",service.entry(1L,e.id()).photoId());assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM collection_photo WHERE state='DELETE'",Integer.class));
    }
    @Test void thumbnailsPreserveAspectAndNeverUpscale() throws Exception {
        var image=images.process(png(900,450));var thumb=ImageIO.read(new java.io.ByteArrayInputStream(image.thumbnail()));
        assertEquals(480,thumb.getWidth());assertEquals(240,thumb.getHeight());
        thumb=ImageIO.read(new java.io.ByteArrayInputStream(images.process(png(100,200)).thumbnail()));
        assertEquals(100,thumb.getWidth());assertEquals(200,thumb.getHeight());
        assertThrows(IllegalArgumentException.class,()->images.process("not a photo".getBytes()));
    }
    @Test void photoOrientationSupportsRotationsAndMirrors(){
        var image=new BufferedImage(2,3,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,0xff0000);image.setRGB(1,2,0x0000ff);
        var rotated=images.orient(image,6);assertEquals(3,rotated.getWidth());assertEquals(2,rotated.getHeight());
        assertEquals(image.getRGB(0,0),rotated.getRGB(2,0));assertEquals(image.getRGB(1,2),rotated.getRGB(0,1));
        assertEquals(image.getRGB(0,0),images.orient(image,2).getRGB(1,0));
    }
    @Test void importsRejectChangedSourceRecordsWithoutMovingThem(){
        Book target=book("대상");Entry e=add("A",null,null);
        service.saveEntry(1L,e.id(),new EntryInput("변경된 A",null,null,null,null,null,null,e.version()));
        assertThrows(ResponseStatusException.class,()->service.importEntries(1L,target.id(),new ImportInput(List.of(e.id()),target.version(),Map.of(e.id(),e.version()))));
        assertNull(service.entry(1L,e.id()).bookId());assertEquals(0,service.book(1L,target.id()).count());
    }
    @Test void legacyLongNamesRemainEditableWithoutTruncatingTheirText(){
        Entry e=add("장소",null,null);
        db.update("UPDATE spot_purchase SET received_place_name=?,item_name=? WHERE id=?","장".repeat(200),"이".repeat(180),e.id());
        Entry legacy=service.entry(1L,e.id());
        Entry saved=service.saveEntry(1L,e.id(),new EntryInput(legacy.placeName(),null,legacy.itemName(),null,"메모 수정",null,null,legacy.version()));
        assertEquals(200,saved.placeName().length());assertEquals(180,saved.itemName().length());
        assertThrows(IllegalArgumentException.class,()->service.saveEntry(1L,e.id(),new EntryInput("새".repeat(121),null,saved.itemName(),null,null,null,null,saved.version())));
    }
    @Test void accountDeletionDuringUploadKeepsThePendingCleanupJob() throws Exception {
        Entry e=add("A",null,null);
        doAnswer(call->{service.clearAccount(1L);db.update("DELETE FROM spot_purchase WHERE user_id=1");db.update("DELETE FROM users WHERE id=1");return null;})
            .when(storage).put(endsWith("original"),any(),anyString());
        assertThrows(EntityNotFoundException.class,()->photos.upload(1L,e.id(),false,e.version(),new MockMultipartFile("file","a.png","image/png",png(20,20))));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM collection_photo WHERE state='DELETE' AND cleanup_after IS NOT NULL",Integer.class));
        photos.cleanup();verify(storage,never()).delete(anyString(),anyString());
    }
    @Test void placeDeletionDetachesReferenceWithoutDeletingCollectedRecord(){
        db.update("INSERT INTO spot VALUES (10,'장소 원래 이름')");db.update("INSERT INTO spot_user VALUES (10,1,10,'내 장소')");
        Entry e=service.saveEntry(1L,null,new EntryInput("받은 장소 이름",10L,null,null,null,null,null,null));
        service.detachSpot(1L,10L);db.update("DELETE FROM spot_user WHERE id=10");
        Entry kept=service.entry(1L,e.id());assertNull(kept.spotUserId());assertEquals("받은 장소 이름",kept.placeName());
    }
    @Test void accountCleanupKeepsFileDeletionJobsAndOtherOwnersBooks(){
        Book mine=book("내 첩");add("내 기록",mine.id(),null);asset("private-photo");
        Book other=service.saveBook(2L,null,new BookInput("다른 사용자",null,null,null));
        service.clearAccount(1L);db.update("DELETE FROM spot_purchase WHERE user_id=1");db.update("DELETE FROM users WHERE id=1");
        assertEquals("DELETE",db.queryForObject("SELECT state FROM collection_photo WHERE id='private-photo'",String.class));
        assertNotNull(service.book(2L,other.id()));
    }
    @Test void failedFileCleanupBacksOffAndAllowsOtherJobsToProceed() throws Exception {
        asset("bad");asset("good");db.update("UPDATE collection_photo SET state='DELETE'");
        doThrow(new java.io.IOException("retry later")).when(storage).delete(eq("local"),eq("bad/original"));
        photos.cleanup();assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM collection_photo",Integer.class));
        assertNotNull(db.queryForObject("SELECT cleanup_after FROM collection_photo WHERE id='bad'",java.sql.Timestamp.class));
        clearInvocations(storage);photos.cleanup();verify(storage,never()).delete(anyString(),anyString());
    }
    private void asset(String id){db.update("INSERT INTO collection_photo(id,user_id,storage,original_key,thumbnail_key,content_type,width,height,byte_size,state) VALUES (?,1,'local',?,?,'image/png',10,10,1,'READY')",id,id+"/original",id+"/thumb");}
    private byte[] png(int w,int h) throws Exception {var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}
}
