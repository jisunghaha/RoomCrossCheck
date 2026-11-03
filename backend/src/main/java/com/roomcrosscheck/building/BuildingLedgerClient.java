package com.roomcrosscheck.building;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.XMLConstants;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import java.io.StringReader;

@Component
public class BuildingLedgerClient {
    private final ObjectMapper mapper;
    private final String key, base;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    public BuildingLedgerClient(ObjectMapper mapper,
        @Value("${building-register.api-key:}") String key,
        @Value("${building-register.base-url:https://apis.data.go.kr/1613000/BldRgstHubService}") String base) {
        this.mapper=mapper; this.key=key; this.base=base;
    }
    public List<JsonNode> fetch(String operation, LedgerQuery q, String dong, String ho) {
        if (key.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"건축물대장 인증키가 설정되지 않았습니다.");
        if (!Set.of("getBrTitleInfo", "getBrExposPubuseAreaInfo").contains(operation)) throw new IllegalArgumentException();
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
            try {
                var response=http.send(HttpRequest.newBuilder(URI.create(base+"/"+operation+"?"+query)).timeout(Duration.ofSeconds(25)).GET().build(),HttpResponse.BodyHandlers.ofString());
                int status=response.statusCode();
                if (status==401 || status==403) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"공식 API 인증이 거부되었습니다. 인증키와 해당 서비스의 활용 신청 승인 상태를 확인해 주세요. (HTTP "+status+")");
                if (status==429) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"공식 API 요청 한도에 도달했습니다. 잠시 후 다시 조회해 주세요. (HTTP 429)");
                if (status!=200) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"공식 조회 서버가 일시적으로 이용 불가합니다. 잠시 후 다시 조회해 주세요. (HTTP "+status+")");
                JsonNode root=parseResponse(response.body());
                String code=root.path("header").path("resultCode").asText("").trim();
                if (code.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"공식 API 응답에 처리 결과 코드가 없습니다. 응답 형식 또는 서비스 상태를 확인해 주세요.");
                if (!Set.of("00","0").contains(code)) throw apiError(code);
                var body=root.path("body"); var rows=body.path("items").path("item");
                int before=items.size();
                if (rows.isArray()) rows.forEach(items::add); else if (rows.isObject()) items.add(rows);
                int total=body.path("totalCount").asInt(-1);
                if (total<0) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"공식 응답의 전체 건수를 확인할 수 없습니다.");
                if (items.size()>=total) return List.copyOf(items);
                if (items.size()==before) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"공식 조회 결과가 일부 누락되었습니다. 다시 조회해 주세요.");
            } catch (ResponseStatusException e) { throw e; }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"공식 조회가 중단되었습니다."); }
            catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"건축물대장 서버 연결에 실패했습니다. 잠시 후 다시 조회해 주세요."); }
        }
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"조회 결과가 너무 많습니다. 동·호실을 구체적으로 선택해 주세요.");
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
