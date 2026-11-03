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
                if (response.statusCode()!=200) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"공식 조회 서버가 응답하지 않습니다. 잠시 후 다시 조회해 주세요.");
                JsonNode root;
                try { root=mapper.readTree(response.body()).path("response"); }
                catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"공식 API 인증 또는 응답 형식을 확인해 주세요."); }
                String code=root.path("header").path("resultCode").asText();
                if (!Set.of("00","0").contains(code)) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"공식 API 조회 실패 (코드 "+code.replaceAll("[^0-9A-Za-z_]", "")+"). 인증 승인 상태와 이용 한도를 확인해 주세요.");
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
    private static String enc(String s) { return URLEncoder.encode(s,StandardCharsets.UTF_8); }
}
