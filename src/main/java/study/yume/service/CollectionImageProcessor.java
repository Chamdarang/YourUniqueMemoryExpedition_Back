package study.yume.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;
import org.springframework.stereotype.Component;
import javax.imageio.*;
import javax.imageio.stream.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Locale;

@Component
public class CollectionImageProcessor {
    public record Image(byte[] original,byte[] thumbnail,String contentType,int width,int height) {}
    public Image process(byte[] bytes) throws IOException {
        if(bytes.length==0 || bytes.length>10*1024*1024) throw new IllegalArgumentException("사진은 10MB 이하로 선택해 주세요.");
        try(ImageInputStream stream=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext()) throw new IllegalArgumentException("JPEG, PNG, WebP 사진을 선택해 주세요.");
            ImageReader reader=readers.next();
            try {
                reader.setInput(stream,false,false);
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                String type=switch(format){case "jpeg","jpg"->"image/jpeg";case "png"->"image/png";case "webp"->"image/webp";default->throw new IllegalArgumentException("JPEG, PNG, WebP 사진을 선택해 주세요.");};
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1 || height<1 || (long)width*height>25_000_000) throw new IllegalArgumentException("사진 해상도는 2,500만 화소 이하여야 합니다.");
                if(reader.getNumImages(true)!=1 || animated(bytes,format)) throw new IllegalArgumentException("움직이는 사진은 지원하지 않습니다.");
                ImageReadParam param=reader.getDefaultReadParam();
                int sample=Math.max(1,Math.max(width,height)/480);
                param.setSourceSubsampling(sample,sample,0,0);
                BufferedImage decoded=reader.read(0,param);
                double scale=Math.min(1,480.0/Math.max(decoded.getWidth(),decoded.getHeight()));
                BufferedImage small=new BufferedImage(Math.max(1,(int)Math.round(decoded.getWidth()*scale)),Math.max(1,(int)Math.round(decoded.getHeight()*scale)),BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics=small.createGraphics();
                try {graphics.setColor(Color.WHITE);graphics.fillRect(0,0,small.getWidth(),small.getHeight());graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);graphics.drawImage(decoded,0,0,small.getWidth(),small.getHeight(),null);}
                finally {graphics.dispose();decoded.flush();}
                int orientation=1;
                try {
                    var directory=ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes)).getFirstDirectoryOfType(ExifIFD0Directory.class);
                    if(directory!=null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) orientation=directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                } catch(Exception ignored) { /* Optional metadata must not invalidate a decoded image. */ }
                BufferedImage normalized=orient(small,orientation);
                ByteArrayOutputStream output=new ByteArrayOutputStream();
                ImageIO.write(normalized,"jpeg",output);
                normalized.flush();small.flush();
                return new Image(bytes,output.toByteArray(),type,width,height);
            } finally {reader.dispose();}
        }
    }
    // Parse chunk headers rather than looking for marker text inside compressed pixels.
    private boolean animated(byte[] bytes,String format) {
        if(format.equals("webp")) {
            for(int i=12;i+8<=bytes.length;) {
                String tag=new String(bytes,i,4,java.nio.charset.StandardCharsets.US_ASCII);
                if(tag.equals("ANIM") || tag.equals("ANMF"))return true;
                long length=Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(bytes,i+4,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt());
                long next=i+8L+length+(length%2);if(next>bytes.length || next<=i)break;i=(int)next;
            }
        } else if(format.equals("png")) {
            for(int i=8;i+12<=bytes.length;) {
                if(new String(bytes,i+4,4,java.nio.charset.StandardCharsets.US_ASCII).equals("acTL"))return true;
                long next=i+12L+Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(bytes,i,4).getInt());
                if(next>bytes.length || next<=i)break;i=(int)next;
            }
        }
        return false;
    }
    BufferedImage orient(BufferedImage source,int orientation) {
        int w=source.getWidth(),h=source.getHeight();
        if(orientation<2 || orientation>8)return source;
        BufferedImage out=new BufferedImage(orientation>=5?h:w,orientation>=5?w:h,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int dx=x,dy=y;
            switch(orientation){
                case 2->dx=w-1-x;case 3->{dx=w-1-x;dy=h-1-y;}case 4->dy=h-1-y;
                case 5->{dx=y;dy=x;}case 6->{dx=h-1-y;dy=x;}case 7->{dx=h-1-y;dy=w-1-x;}case 8->{dx=y;dy=w-1-x;}
            }
            out.setRGB(dx,dy,source.getRGB(x,y));
        }return out;
    }
}
