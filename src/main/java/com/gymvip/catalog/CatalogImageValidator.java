package com.gymvip.catalog;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.RenderingHints;
import java.io.*;
import java.security.*;
import java.util.*;

@Component
public class CatalogImageValidator {
    public static final int MAX_BYTES=5*1024*1024;
    public record Upload(String filename,String mediaType,byte[] bytes,byte[] thumbnail,String sha256) {}
    public Upload read(MultipartFile file) {
        if(file.isEmpty() || file.getSize()>MAX_BYTES) throw new IllegalArgumentException("Cada imagen debe pesar entre 1 byte y 5 MB.");
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new IllegalArgumentException("Adjunta imágenes JPG o PNG válidas.");
            var reader=readers.next();
            try {
                reader.setInput(input,true,true);
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!Set.of("jpeg","jpg","png").contains(format)) throw new IllegalArgumentException("Solo se permiten imágenes JPG y PNG.");
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1 || height<1 || (long)width*height>20_000_000) throw new IllegalArgumentException("La imagen supera el límite de 20 megapíxeles.");
                BufferedImage original=reader.read(0);
                double scale=Math.min(1d,480d/Math.max(width,height));
                var thumbnail=new BufferedImage(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)),BufferedImage.TYPE_INT_ARGB);
                var graphics=thumbnail.createGraphics();
                try { graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);graphics.drawImage(original,0,0,thumbnail.getWidth(),thumbnail.getHeight(),null); }
                finally { graphics.dispose(); }
                var output=new ByteArrayOutputStream();ImageIO.write(thumbnail,"png",output);
                String name=Optional.ofNullable(file.getOriginalFilename()).orElse("imagen").replace('\\','/');
                name=name.substring(name.lastIndexOf('/')+1).replaceAll("[^\\p{L}\\p{N}._ -]","_");
                if(name.isBlank()) name="imagen."+(format.equals("png")?"png":"jpg");
                if(name.length()>160) name=name.substring(name.length()-160);
                byte[] bytes=file.getBytes();
                return new Upload(name,format.equals("png")?"image/png":"image/jpeg",bytes,output.toByteArray(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            } finally {reader.dispose();}
        } catch(IOException|NoSuchAlgorithmException ex) {throw new IllegalArgumentException("No se pudo leer la imagen. Revisa su formato y vuelve a seleccionarla.");}
    }
}
