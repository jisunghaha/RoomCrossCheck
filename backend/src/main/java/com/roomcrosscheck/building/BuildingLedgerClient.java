package com.roomcrosscheck.building;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.XMLConstants;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import java.io.StringReader;

@Component
public class BuildingLedgerClient {
    private static final Logger log=LoggerFactory.getLogger(BuildingLedgerClient.class);
    public record Lookup(List<JsonNode> items, Instant fetchedAt, boolean cached) {}
    private record CacheKey(String operation,String legalCode,int bun,int ji,String landType,String dong,String ho) {}
    private record Cached(Lookup result,Instant expires) {}
    private final Map<CacheKey,Cached> cache=new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final String key, base;
    private final Clock clock;
    private final Duration cacheTtl;
    private final long retryDelayMs;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @Autowired
    public BuildingLedgerClient(ObjectMapper mapper,
        @Value("${building-register.api-key:}") String key,
        @Value("${building-register.base-url:https://apis.data.go.kr/1613000/BldRgstHubService}") String base) {
        this(mapper,key,base,Clock.systemUTC(),Duration.ofMinutes(10),400);
    }
    BuildingLedgerClient(ObjectMapper mapper,String key,String base,Clock clock,Duration cacheTtl,long retryDelayMs) {
        this.mapper=mapper; this.key=key; this.base=base; this.clock=clock; this.cacheTtl=cacheTtl; this.retryDelayMs=retryDelayMs;
    }
    public List<JsonNode> fetch(String operation, LedgerQuery q, String dong, String ho) {
        return lookup(operation,q,dong,ho).items();
    }
    public Lookup lookup(String operation, LedgerQuery q, String dong, String ho) {
        if (key.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"건축물대장 인증키가 설정되지 않았습니다.");
        if (!Set.of("getBrTitleInfo", "getBrExposPubuseAreaInfo").contains(operation)) throw new IllegalArgumentException();
        var cacheKey=new CacheKey(operation,q.legalCode(),Integer.parseInt(q.bun()),Integer.parseInt(q.ji()),q.landType(),dong==null?"":dong.trim(),ho==null?"":ho.trim());
        Instant started=clock.instant();
        Cached previous=cache.get(cacheKey);
        if (previous!=null && previous.expires().isAfter(started)) return new Lookup(previous.result().items(),previous.result().fetchedAt(),true);
        var items = new ArrayList<JsonNode>();
        for (int page=1; page<=30; page++) {
            var params = new LinkedHashMap<String,String>();
            params.put("serviceKey",key); params.put("sigunguCd",q.legalCode().substring(0,5));
            params.put("bjdongCd",q.legalCode().substring(5)); params.put("platGbCd",q.landType());
            params.put("bun",String.format("%04d",Integer.parseInt(q.bun())));
            params.put("ji",String.format("%04d",Integer.parseInt(q.ji())));
            params.put("_type","json"); params.put("numOfRows","1000"); params.put("pageNo",String.valueOf(page));
            if (dong!=null && !dong.isBlank()) params.put("dongNm",dong);
            if (ho!=null && !ho.isBlank()) params.put("hoNm",ho);
            String query = params.entrySet().stream().map(e -> enc(e.getKey())+"="+enc(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
            JsonNode root=requestPage(operation,URI.create(base+"/"+operation+"?"+query));
            var body=root.path("body"); var rows=body.path("items").path("item");
            int before=items.size();
            if (rows.isArray()) rows.forEach(items::add); else if (rows.isObject()) items.add(rows);
            int total=body.path("totalCount").asInt(-1);
            if (items.size()>=total) {
                Lookup result=new Lookup(List.copyOf(items),started,false);
                cache.entrySet().removeIf(e -> !e.getValue().expires().isAfter(clock.instant()));
                if (!cacheTtl.isZero() && !cacheTtl.isNegative()) {
                    if (cache.size()>=256) cache.keySet().stream().findFirst().ifPresent(cache::remove);
                    Duration ttl=items.isEmpty()?Duration.ofSeconds(Math.min(cacheTtl.toSeconds(),60)):cacheTtl;
                    cache.put(cacheKey,new Cached(result,clock.instant().plus(ttl)));
                }
                return result;
            }
            if (items.size()==before) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"공식 조회 결과가 일부 누락되었습니다. 다시 조회해 주세요.");
        }
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"조회 결과가 너무 많습니다. 동·호실을 구체적으로 선택해 주세요.");
    }
    private static class Retryable extends RuntimeException {
        Retryable(String reason) { super(reason); }
    }
    private JsonNode requestPage(String operation,URI uri) {
        for (int attempt=1; attempt<=3; attempt++) {
            try {
                var response=http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).header("Accept","application/json, application/xml;q=0.9").GET().build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status=response.statusCode();
                if (status==401 || status==403) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"공식 API 인증이 거부되었습니다. 인증키와 해당 서비스의 활용 신청 승인 상태를 확인해 주세요. (HTTP "+status+")");
                if (status==429) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"공식 API 요청 한도에 도달했습니다. 잠시 후 다시 조회해 주세요. (HTTP 429)");
                if (Set.of(408,500,502,503,504).contains(status)) throw new Retryable("HTTP "+status);
                if (status!=200) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"공식 API 요청이 거부되었습니다. 입력값을 확인해 주세요. (HTTP "+status+")");
                JsonNode root;
                try { root=parseResponse(response.body()); }
                catch (ResponseStatusException e) { throw new Retryable("비정상 응답 형식"); }
                String code=root.path("header").path("resultCode").asText("").trim();
                if (code.isBlank()) throw new Retryable("결과 코드 누락");
                if (!Set.of("00","0").contains(code)) throw apiError(code);
                if (root.path("body").path("totalCount").asInt(-1)<0) throw new Retryable("전체 건수 누락");
                return root;
            } catch (ResponseStatusException e) { throw e; }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"공식 조회가 중단되었습니다."); }
            catch (java.io.IOException | Retryable e) {
                String reason=e instanceof Retryable?e.getMessage():"연결 오류 또는 시간 초과";
                // Never log the URL, upstream body, credentials or user address.
                log.warn("Building ledger transient response: operation={}, attempt={}, reason={}",operation,attempt,reason);
                if (attempt==3) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"공식 조회 서버의 일시적 오류로 3회 자동 재시도 후에도 조회하지 못했습니다. ("+reason+") 잠시 후 다시 조회해 주세요. 기존 저장 결과는 유지됩니다.");
                try { Thread.sleep(retryDelayMs*attempt); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"공식 조회가 중단되었습니다."); }
            }
        }
        throw new IllegalStateException();
    }
    JsonNode parseResponse(String payload) {
        try {
            String text=payload.strip().replaceFirst("^\\uFEFF", "");
            if (!text.startsWith("<")) {
                JsonNode parsed=mapper.readTree(text);
                if (parsed==null) throw new IllegalArgumentException();
                JsonNode root=parsed.has("response")?parsed.path("response"):parsed;
                if (root.has("header")) return root;
                var gateway=root.path("OpenAPI_ServiceResponse").path("cmmMsgHeader");
                if (!gateway.isMissingNode()) {
                    var converted=mapper.createObjectNode();
                    converted.putObject("header").put("resultCode",gateway.path("returnReasonCode").asText(""));
                    return converted;
                }
                throw new IllegalArgumentException();
            }
            var factory=DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            var builder=factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            var doc=builder.parse(new InputSource(new StringReader(text)));
            String tag=doc.getDocumentElement().getTagName();
            if (!Set.of("response","OpenAPI_ServiceResponse").contains(tag)) throw new IllegalArgumentException();
            var converted=mapper.createObjectNode();
            var header=converted.putObject("header");
            String code=xmlText(doc.getDocumentElement(),"resultCode");
            if (code.isBlank()) code=xmlText(doc.getDocumentElement(),"returnReasonCode");
            header.put("resultCode",code);
            var body=converted.putObject("body");
            body.put("totalCount",xmlText(doc.getDocumentElement(),"totalCount"));
            var items=body.putObject("items").putArray("item");
            var nodes=doc.getElementsByTagName("item");
            for (int i=0;i<nodes.getLength();i++) {
                var row=items.addObject(); var fields=nodes.item(i).getChildNodes();
                for (int j=0;j<fields.getLength();j++) if (fields.item(j) instanceof Element field) row.put(field.getTagName(),field.getTextContent().trim());
            }
            return converted;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"공식 API 응답을 해석하지 못했습니다. JSON·XML이 아닌 응답이거나 서비스 점검 중일 수 있습니다. 잠시 후 다시 조회해 주세요.");
        }
    }
    private static String xmlText(Element root,String name) {
        var nodes=root.getElementsByTagName(name);
        return nodes.getLength()==0?"":nodes.item(0).getTextContent().trim();
    }
    private static ResponseStatusException apiError(String code) {
        String safe=code.replaceAll("[^0-9A-Za-z_]", "");
        if (safe.isBlank()) return new ResponseStatusException(HttpStatus.BAD_GATEWAY,"공식 API에서 알 수 없는 오류가 반환되었습니다. 잠시 후 다시 조회해 주세요.");
        String reason=switch (code) {
            case "20", "21", "30", "31", "32" -> "공식 API 인증·접근 권한 오류입니다. 인증키와 건축물대장정보 서비스 활용 신청 승인 상태를 확인해 주세요.";
            case "22" -> "공식 API 일일 요청 한도에 도달했습니다. 이용 한도를 확인하고 나중에 다시 조회해 주세요.";
            default -> "공식 API에서 조회를 처리하지 못했습니다. 서비스 상태와 입력값을 확인하고 다시 조회해 주세요.";
        };
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,reason+" (코드 "+safe+")");
    }
    private static String enc(String s) { return URLEncoder.encode(s,StandardCharsets.UTF_8); }
}
