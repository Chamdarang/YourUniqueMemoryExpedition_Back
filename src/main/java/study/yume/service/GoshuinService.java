package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import study.yume.dto.goshuin.GoshuinDto.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class GoshuinService {
    private final JdbcTemplate db;
    private static final String ENTRY_SELECT = """
        SELECT p.*, COALESCE(NULLIF(p.received_place_name,''), NULLIF(su.custom_name,''), s.spot_name, '') AS place_label,
               b.name AS book_name
        FROM spot_purchase p LEFT JOIN spot_user su ON su.id=p.spot_user_id
        LEFT JOIN spot s ON s.id=su.spot_id LEFT JOIN goshuin_book b ON b.id=p.goshuin_book_id
        """;
    private static final RowMapper<Entry> ENTRY = (r, n) -> new Entry(
        r.getLong("id"), nullableLong(r,"spot_user_id"), r.getString("place_label"),
        r.getString("item_name"), r.getDate("acquired_date")==null ? null : r.getDate("acquired_date").toLocalDate(),
        r.getString("note"), nullableLong(r,"goshuin_book_id"), r.getString("book_name"),
        (Integer)r.getObject("goshuin_order"), r.getString("photo_id"), r.getLong("record_version"), r.getInt("quantity"));
    private static final RowMapper<Book> BOOK = (r,n) -> new Book(r.getLong("id"),r.getString("name"),
        r.getString("memo"),r.getString("photo_id"),nullableLong(r,"source_purchase_id"),r.getLong("version"),r.getInt("entry_count"));
    private static Long nullableLong(ResultSet r,String field) throws SQLException {
        long value=r.getLong(field); return r.wasNull()?null:value;
    }
    // All collection writes, including legacy ledger writes, serialize per owner.
    public void lockOwner(Long userId) {
        if (db.query("SELECT id FROM users WHERE id=? FOR UPDATE",(r,n)->r.getLong(1),userId).isEmpty())
            throw new EntityNotFoundException("계정을 찾을 수 없습니다.");
    }
    @Transactional(readOnly=true)
    public List<Book> books(Long userId) {
        return db.query("""
            SELECT b.*, (SELECT COUNT(*) FROM spot_purchase p WHERE p.user_id=b.user_id AND p.goshuin_book_id=b.id) AS entry_count
            FROM goshuin_book b WHERE b.user_id=? ORDER BY b.created_at DESC,b.id DESC
            """,BOOK,userId);
    }
    @Transactional(readOnly=true)
    public Book book(Long userId,Long id) {
        return db.query("""
            SELECT b.*, (SELECT COUNT(*) FROM spot_purchase p WHERE p.user_id=b.user_id AND p.goshuin_book_id=b.id) AS entry_count
            FROM goshuin_book b WHERE b.user_id=? AND b.id=?
            """,BOOK,userId,id).stream().findFirst().orElseThrow(()->new EntityNotFoundException("고슈인첩을 찾을 수 없습니다."));
    }
    @Transactional(readOnly=true)
    public Stats stats(Long userId) {
        return new Stats(db.queryForObject("SELECT COUNT(*) FROM spot_purchase WHERE user_id=? AND kind='GOSHUIN' AND status='ACQUIRED' AND goshuin_book_id IS NULL",Long.class,userId));
    }
    @Transactional(readOnly=true)
    public PurchaseSource purchaseSource(Long userId,Long id) {
        return db.query("SELECT p.id,p.item_name,b.id AS linked_book FROM spot_purchase p LEFT JOIN goshuin_book b ON b.source_purchase_id=p.id AND b.user_id=p.user_id WHERE p.user_id=? AND p.id=? AND p.kind='GOSHUINCHO' AND p.status='ACQUIRED'",
            (r,n)->new PurchaseSource(r.getLong("id"),r.getString("item_name"),nullableLong(r,"linked_book")),userId,id)
            .stream().findFirst().orElseThrow(()->new EntityNotFoundException("받은 고슈인첩 구매 기록을 찾을 수 없습니다."));
    }
    public Book saveBook(Long userId,Long id,BookInput input) {
        lockOwner(userId);
        String name=text(input.name(),80,true,"첩 이름"),memo=text(input.memo(),2000,false,"메모");
        if(id==null) {
            Long source=input.sourcePurchaseId();
            if(source!=null) {
                var rows=db.queryForList("SELECT id FROM spot_purchase WHERE id=? AND user_id=? AND kind='GOSHUINCHO'",source,userId);
                if(rows.isEmpty()) throw new IllegalArgumentException("본인의 고슈인첩 구매 기록을 선택해 주세요.");
                var existing=db.query("SELECT id FROM goshuin_book WHERE user_id=? AND source_purchase_id=?",(r,n)->r.getLong(1),userId,source);
                if(!existing.isEmpty()) return book(userId,existing.get(0));
            }
            id=insert("INSERT INTO goshuin_book(user_id,name,memo,source_purchase_id) VALUES (?,?,?,?)",userId,name,memo,source);
        } else {
            checkVersion(input.version(),book(userId,id).version());
            db.update("UPDATE goshuin_book SET name=?,memo=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND id=?",name,memo,userId,id);
        }
        return book(userId,id);
    }
    public void deleteBook(Long userId,Long id,boolean deleteEntries,Long version) {
        lockOwner(userId);
        Book b=book(userId,id); checkVersion(version,b.version());
        retirePhoto(b.photoId());
        if(deleteEntries) {
            db.query("SELECT photo_id FROM spot_purchase WHERE user_id=? AND goshuin_book_id=?",(r,n)->r.getString(1),userId,id).forEach(this::retirePhoto);
            db.update("DELETE FROM spot_purchase WHERE user_id=? AND goshuin_book_id=?",userId,id);
        } else {
            db.update("UPDATE spot_purchase SET goshuin_book_id=NULL,goshuin_order=NULL,record_version=record_version+1 WHERE user_id=? AND goshuin_book_id=?",userId,id);
        }
        db.update("DELETE FROM goshuin_book WHERE user_id=? AND id=?",userId,id);
    }
    @Transactional(readOnly=true)
    public Entry entry(Long userId,Long id) {
        return db.query(ENTRY_SELECT+" WHERE p.user_id=? AND p.id=? AND p.kind='GOSHUIN' AND p.status='ACQUIRED'",ENTRY,userId,id)
            .stream().findFirst().orElseThrow(()->new EntityNotFoundException("받은 고슈인을 찾을 수 없습니다."));
    }
    @Transactional(readOnly=true)
    public Entries entries(Long userId,Long bookId,boolean unbound,String keyword,int page) {
        if(bookId!=null) book(userId,bookId);
        if(page<0 || page>100000) throw new IllegalArgumentException("잘못된 페이지입니다.");
        String where=" WHERE p.user_id=? AND p.kind='GOSHUIN' AND p.status='ACQUIRED'";
        List<Object> args=new ArrayList<>(); args.add(userId);
        if(bookId!=null){where+=" AND p.goshuin_book_id=?";args.add(bookId);}
        else if(unbound) where+=" AND p.goshuin_book_id IS NULL";
        String term=text(keyword,120,false,"검색어");
        if(term!=null) {
            where+=" AND (LOWER(COALESCE(p.item_name,'')) LIKE ? ESCAPE '!' OR LOWER(COALESCE(p.received_place_name,su.custom_name,s.spot_name,'')) LIKE ? ESCAPE '!')";
            String match="%"+term.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            args.add(match);args.add(match);
        }
        String from=ENTRY_SELECT.substring(ENTRY_SELECT.indexOf("FROM spot_purchase"));
        long total=Objects.requireNonNull(db.queryForObject("SELECT COUNT(*) "+from+where,Long.class,args.toArray()));
        String sort=bookId!=null?" ORDER BY p.goshuin_order,p.id":" ORDER BY CASE WHEN p.acquired_date IS NULL THEN 1 ELSE 0 END,p.acquired_date DESC,p.created_at DESC,p.id DESC";
        args.add(20);args.add(page*20);
        var content=db.query(ENTRY_SELECT+where+sort+" LIMIT ? OFFSET ?",ENTRY,args.toArray());
        return new Entries(content,total,page,(int)((total+19)/20));
    }
    @Transactional(readOnly=true)
    public List<Entry> order(Long userId,Long bookId) {
        book(userId,bookId);
        return db.query(ENTRY_SELECT+" WHERE p.user_id=? AND p.goshuin_book_id=? ORDER BY p.goshuin_order,p.id",ENTRY,userId,bookId);
    }
    public Entry saveEntry(Long userId,Long id,EntryInput in) {
        lockOwner(userId);
        Entry old=id==null?null:entry(userId,id);
        if(old!=null) checkVersion(in.version(),old.version());
        int placeLimit=old!=null && Objects.equals(old.placeName(),in.placeName())?200:120;
        int nameLimit=old!=null && Objects.equals(old.itemName(),in.itemName())?200:120;
        String place=text(in.placeName(),placeLimit,true,"받은 장소"),name=text(in.itemName(),nameLimit,false,"고슈인 이름"),note=text(in.note(),2000,false,"메모");
        if(in.acquiredDate()!=null && in.acquiredDate().isAfter(LocalDate.now(ZoneId.of("Asia/Seoul"))) &&
            (old==null || !in.acquiredDate().equals(old.acquiredDate())))
            throw new IllegalArgumentException("받은 날짜는 오늘 이전으로 입력해 주세요.");
        Long spot=in.spotUserId()!=null && in.spotUserId()>0 ? in.spotUserId():null;
        if(spot!=null && db.queryForObject("SELECT COUNT(*) FROM spot_user WHERE user_id=? AND id=?",Integer.class,userId,spot)==0)
            throw new IllegalArgumentException("내 장소를 선택해 주세요.");
        if(in.bookId()!=null) book(userId,in.bookId());
        if(id==null) id=insert("""
            INSERT INTO spot_purchase(user_id,spot_user_id,kind,status,quantity,received_place_name,item_name,acquired_date,note)
            VALUES (?,?,'GOSHUIN','ACQUIRED',1,?,?,?,?)
            """,userId,spot,place,name,in.acquiredDate(),note);
        else db.update("""
            UPDATE spot_purchase SET spot_user_id=?,received_place_name=?,item_name=?,acquired_date=?,note=?,
            record_version=record_version+1,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND id=?
            """,spot,place,name,in.acquiredDate(),note,userId,id);
        Long oldBook=old==null?null:old.bookId();
        if(!Objects.equals(oldBook,in.bookId()) || in.position()!=null) {
            db.update("UPDATE spot_purchase SET goshuin_book_id=NULL,goshuin_order=NULL WHERE user_id=? AND id=?",userId,id);
            if(oldBook!=null) renumber(userId,oldBook,null,null);
            if(in.bookId()!=null) renumber(userId,in.bookId(),id,in.position());
        } else if(oldBook!=null) touchBook(userId,oldBook);
        return entry(userId,id);
    }
    public void deleteEntry(Long userId,Long id,Long version) {
        lockOwner(userId); Entry e=entry(userId,id);checkVersion(version,e.version());
        retirePhoto(e.photoId());
        db.update("DELETE FROM spot_purchase WHERE user_id=? AND id=?",userId,id);
        if(e.bookId()!=null) renumber(userId,e.bookId(),null,null);
    }
    public Book reorder(Long userId,Long bookId,OrderInput input) {
        lockOwner(userId); Book b=book(userId,bookId); checkVersion(input.version(),b.version());
        List<Long> actual=ids(userId,bookId);
        if(input.ids()==null || input.ids().size()!=actual.size() || new HashSet<>(input.ids()).size()!=actual.size() ||
            !new HashSet<>(actual).equals(new HashSet<>(input.ids())))
            throw new IllegalArgumentException("첩 전체 기록의 순서를 확인해 주세요.");
        applyOrder(userId,bookId,input.ids());return book(userId,bookId);
    }
    public Book importEntries(Long userId,Long bookId,ImportInput input) {
        lockOwner(userId); Book b=book(userId,bookId);checkVersion(input.version(),b.version());
        if(input.ids()==null || input.ids().isEmpty() || input.ids().size()>200 || new HashSet<>(input.ids()).size()!=input.ids().size())
            throw new IllegalArgumentException("넣을 기록을 1~200개 선택해 주세요.");
        if(input.recordVersions()==null || !input.recordVersions().keySet().containsAll(input.ids()))
            throw new IllegalArgumentException("선택한 기록의 최신 상태를 확인해 주세요.");
        Set<Long> sources=new HashSet<>();List<Long> target=ids(userId,bookId);
        for(Long id:input.ids()) {
            Entry e=entry(userId,id);
            checkVersion(input.recordVersions().get(id),e.version());
            if(Objects.equals(e.bookId(),bookId)) throw new IllegalArgumentException("이미 이 첩에 있는 기록입니다.");
            if(e.placeName()==null || e.placeName().isBlank()) throw new IllegalArgumentException("받은 장소가 없는 기록은 먼저 장소를 입력해 주세요.");
            if(e.bookId()!=null) sources.add(e.bookId());
            db.update("UPDATE spot_purchase SET goshuin_book_id=NULL,goshuin_order=NULL WHERE user_id=? AND id=?",userId,id);
            target.add(id);
        }
        sources.forEach(source->renumber(userId,source,null,null));applyOrder(userId,bookId,target);
        return book(userId,bookId);
    }
    private List<Long> ids(Long userId,Long bookId) {
        return new ArrayList<>(db.query("SELECT id FROM spot_purchase WHERE user_id=? AND goshuin_book_id=? ORDER BY goshuin_order,id",(r,n)->r.getLong(1),userId,bookId));
    }
    public void renumber(Long userId,Long bookId,Long insertId,Integer position) {
        List<Long> ordered=ids(userId,bookId);
        if(insertId!=null) {
            int index=position==null?ordered.size():position-1;
            if(index<0 || index>ordered.size()) throw new IllegalArgumentException("넣을 순서를 확인해 주세요.");
            ordered.add(index,insertId);
        }
        applyOrder(userId,bookId,ordered);
    }
    private void applyOrder(Long userId,Long bookId,List<Long> ids) {
        for(int i=0;i<ids.size();i++) db.update("""
            UPDATE spot_purchase SET goshuin_book_id=?,goshuin_order=?,record_version=record_version+1
            WHERE user_id=? AND id=?
            """,bookId,i+1,userId,ids.get(i));
        touchBook(userId,bookId);
    }
    public void touchBook(Long userId,Long id) {
        if(id!=null) db.update("UPDATE goshuin_book SET version=version+1,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND id=?",userId,id);
    }
    public void retirePhoto(String id) {
        if(id!=null) db.update("UPDATE collection_photo SET cleanup_after=CASE WHEN state='PENDING' THEN ? ELSE cleanup_after END,state='DELETE' WHERE id=?",
            java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(300)),id);
    }
    public void clearAccount(Long userId) {
        lockOwner(userId);
        db.update("UPDATE collection_photo SET cleanup_after=CASE WHEN state='PENDING' THEN ? ELSE cleanup_after END,state='DELETE' WHERE user_id=?",
            java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(300)),userId);
        db.update("UPDATE spot_purchase SET goshuin_book_id=NULL,goshuin_order=NULL WHERE user_id=?",userId);
        db.update("DELETE FROM goshuin_book WHERE user_id=?",userId);
    }
    public void detachSpot(Long userId,Long spotId) {
        lockOwner(userId);
        db.update("""
            UPDATE spot_purchase SET spot_user_id=NULL,record_version=record_version+1
            WHERE user_id=? AND spot_user_id=? AND kind='GOSHUIN'
            """,userId,spotId);
    }
    public void legacyChanged(Long userId,Long bookId) {touchBook(userId,bookId);}
    public static void checkVersion(Long expected,long actual) {
        if(expected==null || expected!=actual) throw new ResponseStatusException(HttpStatus.CONFLICT,"기록이 변경됐습니다. 최신 목록을 불러온 후 다시 확인해 주세요.");
    }
    public static String text(String value,int max,boolean required,String label) {
        String s=value==null?null:value.trim();
        if(s!=null && s.isEmpty()) s=null;
        if(required && s==null) throw new IllegalArgumentException(label+"을 입력해 주세요.");
        if(s!=null && s.codePointCount(0,s.length())>max) throw new IllegalArgumentException(label+"은 "+max+"자 이내로 입력해 주세요.");
        return s;
    }
    private Long insert(String sql,Object...args) {
        GeneratedKeyHolder key=new GeneratedKeyHolder();
        db.update(c->{PreparedStatement p=c.prepareStatement(sql,new String[]{"id"});
            for(int i=0;i<args.length;i++) p.setObject(i+1,args[i]);return p;},key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
}
