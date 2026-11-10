package com.gymvip.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.exception.*;
import com.gymvip.service.AuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import static com.gymvip.catalog.CatalogDtos.*;

@Service
public class CatalogService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AuditService audit;
    private final CatalogImageValidator images;
    private final Clock clock;
    public CatalogService(JdbcTemplate jdbc,ObjectMapper json,AuditService audit,CatalogImageValidator images,Clock clock) {
        this.jdbc=jdbc;this.json=json;this.audit=audit;this.images=images;this.clock=clock;
    }
    private LocalDateTime now() {return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);}
    private LocalDate today() {return LocalDate.now(clock.withZone(ZoneId.of("America/Lima")));}
    private String clean(String value) {return value==null?"":value.trim();}
    private String snapshot(Object value) {try{return json.writeValueAsString(value);}catch(JsonProcessingException ex){throw new IllegalStateException("No se pudo guardar el historial.",ex);}}
    private void paging(int page,int size) {if(page<0||size<1||size>60)throw new IllegalArgumentException("Paginación inválida.");}
    private Supplement mapSupplement(ResultSet r,int ignored) throws SQLException {
        UUID id=r.getObject("id",UUID.class);
        var photos=jdbc.query("SELECT id,filename,media_type FROM supplement_images WHERE supplement_id=? AND active ORDER BY position,id",
            (image,n)->new Image(image.getObject(1,UUID.class),image.getString(2),image.getString(3)),id);
        String barcode = null;
        try { barcode = r.getString("barcode"); } catch (Exception ignoredCol) {}
        Timestamp ts = r.getTimestamp("updated_at_utc");
        LocalDateTime updatedAt = ts != null ? ts.toLocalDateTime() : null;
        return new Supplement(id,r.getString("name"),r.getString("brand"),r.getString("category"),r.getString("presentation"),r.getString("net_content"),r.getString("flavor"),r.getString("description"),r.getString("ingredients"),r.getBigDecimal("price_pen"),r.getString("availability"),r.getBoolean("published"),r.getLong("version"),updatedAt,photos,barcode);
    }
    @Transactional(readOnly=true) public Supplement supplement(UUID id,boolean customer) {
        return jdbc.query("SELECT * FROM supplements WHERE id=?"+(customer?" AND published":""),this::mapSupplement,id).stream().findFirst().orElseThrow(()->new ResourceNotFoundException("Suplemento no encontrado."));
    }
    @Transactional(readOnly=true) public Page<Supplement> supplements(String query,String category,String availability,Boolean published,BigDecimal minPrice,BigDecimal maxPrice,boolean withPromotion,int page,int size,boolean customer) {
        paging(page,size);
        if(minPrice!=null&&minPrice.signum()<0||maxPrice!=null&&maxPrice.signum()<0||minPrice!=null&&maxPrice!=null&&minPrice.compareTo(maxPrice)>0)throw new IllegalArgumentException("Rango de precios inválido.");
        StringBuilder where=new StringBuilder(" WHERE 1=1");List<Object> args=new ArrayList<>();
        if(customer){where.append(" AND published");}else if(published!=null){where.append(" AND published=?");args.add(published);}
        if(!clean(query).isEmpty()){where.append(" AND (position(lower(?) in lower(name))>0 OR position(lower(?) in lower(brand))>0)");args.add(query.trim());args.add(query.trim());}
        if(!clean(category).isEmpty()){where.append(" AND category=?");args.add(category);}
        if(!clean(availability).isEmpty()){where.append(" AND availability=?");args.add(availability);}
        if(minPrice!=null){where.append(" AND price_pen>=?");args.add(minPrice);}
        if(maxPrice!=null){where.append(" AND price_pen<=?");args.add(maxPrice);}
        if(withPromotion){where.append(" AND EXISTS(SELECT 1 FROM supplement_promotions p WHERE p.supplement_id=supplements.id AND p.enabled AND p.start_date<=? AND p.end_date>=?)");args.add(today());args.add(today());}
        long count=jdbc.queryForObject("SELECT count(*) FROM supplements"+where,Long.class,args.toArray());
        args.add(size);args.add((long)page*size);
        var rows=jdbc.query("SELECT * FROM supplements"+where+" ORDER BY lower(name),lower(brand),id LIMIT ? OFFSET ?",this::mapSupplement,args.toArray());
        return new Page<>(rows,count,page,(int)((count+size-1)/size));
    }
    private void lockSupplement(UUID id) {
        if(jdbc.query("SELECT id FROM supplements WHERE id=? FOR UPDATE",(r,n)->r.getObject(1,UUID.class),id).isEmpty())throw new ResourceNotFoundException("Suplemento no encontrado.");
    }
    private void changed(Supplement item,String actor) {
        jdbc.update("INSERT INTO supplement_changes(id,supplement_id,version,actor,changed_at_utc,snapshot) VALUES (?,?,?,?,?,?::jsonb)",UUID.randomUUID(),item.id(),item.version(),actor,now(),snapshot(item));
        audit.recordEvent(actor,"UPDATE_SUPPLEMENT_CATALOG","Supplement",item.id().toString(),"Versión "+item.version());
    }
    @Transactional public Supplement saveSupplement(UUID id,SupplementInput input,List<MultipartFile> files,String actor) {
        if(!input.reviewed())throw new IllegalArgumentException("Confirma que la información coincide con el rotulado antes de guardar.");
        List<UUID> keep=input.keepImageIds()==null?List.of():input.keepImageIds();
        if(new HashSet<>(keep).size()!=keep.size())throw new IllegalArgumentException("Hay imágenes repetidas.");
        var uploads=files==null?List.<CatalogImageValidator.Upload>of():files.stream().map(images::read).toList();
        if(keep.size()+uploads.size()<1||keep.size()+uploads.size()>5)throw new IllegalArgumentException("Adjunta de 1 a 5 imágenes por suplemento.");
        boolean creating=id==null;
        if(creating){id=UUID.randomUUID();if(!keep.isEmpty())throw new IllegalArgumentException("Las imágenes conservadas no corresponden a un registro nuevo.");}
        else {
            lockSupplement(id);var current=supplement(id,false);
            if(input.version()==null||input.version()!=current.version())throw new ConflictException("El suplemento cambió. Recarga y revisa los cambios antes de guardar.");
            Set<UUID> owned=new HashSet<>(current.images().stream().map(Image::id).toList());
            if(!owned.containsAll(keep))throw new IllegalArgumentException("Una imagen no pertenece a la versión actual del suplemento.");
        }
        if(!creating){
            BigDecimal largestDiscount=jdbc.queryForObject("SELECT coalesce(max(reference_price_pen-offer_price_pen),0) FROM supplement_promotions WHERE supplement_id=? AND enabled AND end_date>=?",BigDecimal.class,id,today());
            if(input.pricePen().compareTo(largestDiscount)<=0)throw new ConflictException("El precio debe superar el descuento de S/ "+largestDiscount+" de las promociones habilitadas. Modifica primero la promoción.");
        }
        if(creating)jdbc.update("""
            INSERT INTO supplements(id,name,brand,category,presentation,net_content,flavor,description,ingredients,price_pen,availability,published,created_at_utc,updated_at_utc)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,clean(input.name()),clean(input.brand()),input.category(),clean(input.presentation()),clean(input.netContent()),clean(input.flavor()),clean(input.description()),clean(input.ingredients()),input.pricePen(),input.availability(),input.published(),now(),now());
        else jdbc.update("""
            UPDATE supplements SET name=?,brand=?,category=?,presentation=?,net_content=?,flavor=?,description=?,ingredients=?,price_pen=?,availability=?,published=?,version=version+1,updated_at_utc=? WHERE id=?
            """,clean(input.name()),clean(input.brand()),input.category(),clean(input.presentation()),clean(input.netContent()),clean(input.flavor()),clean(input.description()),clean(input.ingredients()),input.pricePen(),input.availability(),input.published(),now(),id);
        jdbc.update("UPDATE supplement_images SET active=false WHERE supplement_id=?",id);
        int position=0;List<UUID> ordered=new ArrayList<>(keep);
        for(UUID image:keep)jdbc.update("UPDATE supplement_images SET active=true,position=? WHERE id=? AND supplement_id=?",position++,image,id);
        for(var upload:uploads){UUID uploadedId=UUID.randomUUID();ordered.add(uploadedId);jdbc.update("INSERT INTO supplement_images(id,supplement_id,filename,media_type,bytes,thumbnail,sha256,position) VALUES (?,?,?,?,?,?,?,?)",uploadedId,id,upload.filename(),upload.mediaType(),upload.bytes(),upload.thumbnail(),upload.sha256(),position++);}
        if(input.primaryImageKey()!=null){
            UUID main;try{ main=input.primaryImageKey().startsWith("upload:")?ordered.get(keep.size()+Integer.parseInt(input.primaryImageKey().substring(7))):UUID.fromString(input.primaryImageKey()); }catch(RuntimeException ex){throw new IllegalArgumentException("Imagen principal inválida.");}
            if(!ordered.remove(main))throw new IllegalArgumentException("Selecciona una imagen principal del suplemento.");
            ordered.addFirst(main);int order=0;for(UUID photo:ordered)jdbc.update("UPDATE supplement_images SET position=? WHERE id=?",order++,photo);
        }
        var saved=supplement(id,false);changed(saved,actor);return saved;
    }
    @Transactional public Supplement state(UUID id,StateInput input,String actor) {
        lockSupplement(id);
        if(input.version()!=supplement(id,false).version())throw new ConflictException("El suplemento cambió. Recarga antes de modificar su disponibilidad.");
        jdbc.update("UPDATE supplements SET availability=?,published=?,version=version+1,updated_at_utc=? WHERE id=?",input.availability(),input.published(),now(),id);
        var saved=supplement(id,false);changed(saved,actor);return saved;
    }
    @Transactional(readOnly=true) public List<Change> supplementHistory(UUID id,int page) {
        paging(page,20);supplement(id,false);
        return jdbc.query("SELECT version,actor,changed_at_utc,snapshot::text FROM supplement_changes WHERE supplement_id=? ORDER BY version DESC LIMIT 20 OFFSET ?",(r,n)->new Change(r.getLong(1),r.getString(2),r.getObject(3,LocalDateTime.class),r.getString(4)),id,(long)page*20);
    }
    @Transactional(readOnly=true) public BinaryImage image(UUID supplementId,UUID imageId,boolean thumbnail,boolean customer) {
        supplement(supplementId,customer);
        String column=thumbnail?"thumbnail":"bytes";
        return jdbc.query("SELECT "+column+",media_type,sha256 FROM supplement_images WHERE id=? AND supplement_id=?"+(customer?" AND active":""),(r,n)->new BinaryImage(r.getBytes(1),thumbnail?"image/png":r.getString(2),(thumbnail?"thumb-":"")+r.getString(3)),imageId,supplementId).stream().findFirst().orElseThrow(()->new ResourceNotFoundException("Imagen no encontrada."));
    }
    private String promotionState(LocalDate start,LocalDate end,boolean enabled) {
        return !enabled?"CANCELADA":today().isBefore(start)?"PROGRAMADA":today().isAfter(end)?"FINALIZADA":"VIGENTE";
    }
    private Promotion mapPromotion(ResultSet r,int ignored) throws SQLException {
        Supplement item=supplement(r.getObject("supplement_id",UUID.class),false);
        BigDecimal savings=r.getBigDecimal("reference_price_pen").subtract(r.getBigDecimal("offer_price_pen"));
        String state=promotionState(r.getObject("start_date",LocalDate.class),r.getObject("end_date",LocalDate.class),r.getBoolean("enabled"));
        boolean current=Set.of("VIGENTE","PROGRAMADA").contains(state);
        BigDecimal reference=current?item.pricePen():r.getBigDecimal("reference_price_pen");
        BigDecimal offer=current?item.pricePen().subtract(savings):r.getBigDecimal("offer_price_pen");
        return new Promotion(r.getObject("id",UUID.class),item,r.getString("name"),r.getString("description"),r.getString("conditions"),reference,offer,savings,r.getObject("start_date",LocalDate.class),r.getObject("end_date",LocalDate.class),state,r.getBoolean("enabled"),r.getLong("version"),r.getObject("updated_at_utc",LocalDateTime.class));
    }
    @Transactional(readOnly=true) public Promotion promotion(UUID id,boolean customer) {
        return jdbc.query("SELECT p.* FROM supplement_promotions p JOIN supplements s ON s.id=p.supplement_id WHERE p.id=?"+(customer?" AND p.enabled AND p.start_date<=? AND p.end_date>=? AND s.published":""),this::mapPromotion,customer?new Object[]{id,today(),today()}:new Object[]{id}).stream().findFirst().orElseThrow(()->new ResourceNotFoundException("Promoción no encontrada o fuera de vigencia."));
    }
    @Transactional(readOnly=true) public Page<Promotion> promotions(String state,UUID supplementId,int page,int size,boolean customer) {
        paging(page,size);List<Object> args=new ArrayList<>();StringBuilder where=new StringBuilder(" WHERE 1=1");
        String mode=clean(state);
        if(customer||mode.equals("VIGENTE")){where.append(" AND p.enabled AND p.start_date<=? AND p.end_date>=?");args.add(today());args.add(today());}
        else if(mode.equals("PROGRAMADA")){where.append(" AND p.enabled AND p.start_date>?");args.add(today());}
        else if(mode.equals("FINALIZADA")){where.append(" AND (NOT p.enabled OR p.end_date<?)");args.add(today());}
        else if(!mode.isEmpty()&&!mode.equals("TODAS"))throw new IllegalArgumentException("Estado de promoción inválido.");
        if(customer)where.append(" AND s.published");
        if(supplementId!=null){where.append(" AND p.supplement_id=?");args.add(supplementId);}
        String from=" FROM supplement_promotions p JOIN supplements s ON s.id=p.supplement_id";
        long count=jdbc.queryForObject("SELECT count(*)"+from+where,Long.class,args.toArray());args.add(size);args.add((long)page*size);
        return new Page<>(jdbc.query("SELECT p.*"+from+where+" ORDER BY p.start_date DESC,p.id LIMIT ? OFFSET ?",this::mapPromotion,args.toArray()),count,page,(int)((count+size-1)/size));
    }
    @Transactional public Promotion savePromotion(UUID id,PromotionInput input,String actor) {
        if(!input.reviewed())throw new IllegalArgumentException("Revisa y confirma los datos de la promoción.");
        if(!input.endDate().isAfter(input.startDate()))throw new IllegalArgumentException("El fin de vigencia debe ser posterior al inicio.");
        Promotion previous=null;
        if(id!=null){
            if(jdbc.query("SELECT id FROM supplement_promotions WHERE id=? FOR UPDATE",(r,n)->r.getObject(1,UUID.class),id).isEmpty())throw new ResourceNotFoundException("Promoción no encontrada.");
            previous=promotion(id,false);
            if(input.version()==null||input.version()!=previous.version())throw new ConflictException("La promoción cambió. Recarga antes de guardar.");
        }
        var selected=jdbc.query("SELECT id FROM supplements WHERE id IN (?,?) ORDER BY id FOR UPDATE",(r,n)->r.getObject(1,UUID.class),input.supplementId(),previous==null?input.supplementId():previous.supplement().id());
        if(!selected.contains(input.supplementId()))throw new ResourceNotFoundException("Selecciona un suplemento registrado.");
        Supplement item=supplement(input.supplementId(),false);
        if(input.supplementVersion()==null||input.supplementVersion()!=item.version())throw new ConflictException("El suplemento cambió. Vuelve a seleccionarlo y revisa el precio antes de guardar.");
        BigDecimal reference=item.pricePen();
        if(input.offerPricePen().compareTo(reference)>=0)throw new IllegalArgumentException("El precio de oferta debe ser menor al precio de referencia: S/ "+reference+".");
        if(input.enabled()&&jdbc.queryForObject("SELECT count(*) FROM supplement_promotions WHERE supplement_id=? AND enabled AND (?::uuid IS NULL OR id<>?::uuid) AND start_date<=? AND end_date>=?",Long.class,item.id(),id,id,input.endDate(),input.startDate())>0)
            throw new ConflictException("Este suplemento ya tiene una promoción que coincide con esas fechas. Revisa o desactiva la anterior.");
        if(id==null){id=UUID.randomUUID();jdbc.update("INSERT INTO supplement_promotions(id,supplement_id,name,description,conditions,reference_price_pen,offer_price_pen,start_date,end_date,enabled,created_at_utc,updated_at_utc) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",id,item.id(),clean(input.name()),clean(input.description()),clean(input.conditions()),reference,input.offerPricePen(),input.startDate(),input.endDate(),input.enabled(),now(),now());}
        else jdbc.update("UPDATE supplement_promotions SET supplement_id=?,name=?,description=?,conditions=?,reference_price_pen=?,offer_price_pen=?,start_date=?,end_date=?,enabled=?,version=version+1,updated_at_utc=? WHERE id=?",item.id(),clean(input.name()),clean(input.description()),clean(input.conditions()),reference,input.offerPricePen(),input.startDate(),input.endDate(),input.enabled(),now(),id);
        var saved=promotion(id,false);
        jdbc.update("INSERT INTO promotion_changes(id,promotion_id,version,actor,changed_at_utc,snapshot) VALUES (?,?,?,?,?,?::jsonb)",UUID.randomUUID(),id,saved.version(),actor,now(),snapshot(saved));
        audit.recordEvent(actor,"UPDATE_SUPPLEMENT_PROMOTION","Promotion",id.toString(),"Versión "+saved.version());return saved;
    }
    @Transactional public Promotion deactivatePromotion(UUID id,PromotionCancellation input,String actor) {
        if(jdbc.query("SELECT id FROM supplement_promotions WHERE id=? FOR UPDATE",(r,n)->r.getObject(1,UUID.class),id).isEmpty())throw new ResourceNotFoundException("Promoción no encontrada.");
        Promotion current=promotion(id,false);lockSupplement(current.supplement().id());
        if(input.version()!=current.version())throw new ConflictException("La promoción cambió. Recarga antes de desactivarla.");
        jdbc.update("UPDATE supplement_promotions SET enabled=false,version=version+1,updated_at_utc=? WHERE id=?",now(),id);
        Promotion saved=promotion(id,false);
        jdbc.update("INSERT INTO promotion_changes(id,promotion_id,version,actor,changed_at_utc,snapshot) VALUES (?,?,?,?,?,?::jsonb)",UUID.randomUUID(),id,saved.version(),actor,now(),snapshot(saved));
        audit.recordEvent(actor,"DEACTIVATE_SUPPLEMENT_PROMOTION","Promotion",id.toString(),"Promoción desactivada; descuento conservado.");return saved;
    }
    @Transactional(readOnly=true) public List<Change> promotionHistory(UUID id,int page) {
        paging(page,20);promotion(id,false);
        return jdbc.query("SELECT version,actor,changed_at_utc,snapshot::text FROM promotion_changes WHERE promotion_id=? ORDER BY version DESC LIMIT 20 OFFSET ?",(r,n)->new Change(r.getLong(1),r.getString(2),r.getObject(3,LocalDateTime.class),r.getString(4)),id,(long)page*20);
    }

    @Transactional(readOnly=true)
    public Supplement scan(String code) {
        String cleanCode = clean(code);
        if (cleanCode.isEmpty()) throw new IllegalArgumentException("Código de producto o código de barras no provisto.");

        var byBarcode = jdbc.query("SELECT * FROM supplements WHERE barcode = ?", this::mapSupplement, cleanCode);
        if (!byBarcode.isEmpty()) return byBarcode.get(0);

        try {
            UUID id = UUID.fromString(cleanCode);
            var byId = jdbc.query("SELECT * FROM supplements WHERE id = ?", this::mapSupplement, id);
            if (!byId.isEmpty()) return byId.get(0);
        } catch (IllegalArgumentException ignored) {}

        var byName = jdbc.query("SELECT * FROM supplements WHERE lower(name) = lower(?)", this::mapSupplement, cleanCode);
        if (!byName.isEmpty()) return byName.get(0);

        throw new ResourceNotFoundException("No se encontró ningún producto con el código o código de barras: " + cleanCode);
    }

    @Transactional
    public Supplement updateBarcode(UUID id, String barcode, String actor) {
        Supplement existing = supplement(id, false);
        String cleanBarcode = clean(barcode);
        if (!cleanBarcode.isEmpty()) {
            Long count = jdbc.queryForObject("SELECT count(*) FROM supplements WHERE barcode = ? AND id <> ?", Long.class, cleanBarcode, id);
            if (count != null && count > 0) {
                throw new ConflictException("El código de barras " + cleanBarcode + " ya está asignado a otro producto.");
            }
        }
        jdbc.update("UPDATE supplements SET barcode = ?, version = version + 1, updated_at_utc = ? WHERE id = ?", cleanBarcode.isEmpty() ? null : cleanBarcode, now(), id);
        Supplement updated = supplement(id, false);
        audit.recordEvent(actor, "UPDATE_SUPPLEMENT_BARCODE", "Supplement", id.toString(), "Código de barras: " + cleanBarcode);
        return updated;
    }
}
