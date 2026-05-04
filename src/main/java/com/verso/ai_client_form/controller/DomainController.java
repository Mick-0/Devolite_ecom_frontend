package com.verso.ai_client_form.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class DomainController {

    private static final Pattern HOST_RE = Pattern.compile(
        "^(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63}$"
    );

    private static final URI IANA_RDAP_BOOTSTRAP = URI.create("https://data.iana.org/rdap/dns.json");
    private static final long BOOTSTRAP_TTL_MS = Duration.ofHours(12).toMillis();
    private static volatile long bootstrapLoadedAt = 0L;
    private static volatile Map<String, List<String>> bootstrapByTld = Map.of();
    private static final Object BOOTSTRAP_LOCK = new Object();
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Map<String, List<String>> RDAP_FASTPATH = Map.of(
        "it", List.of("https://rdap.nic.it/domain/"),
        "com", List.of("https://rdap.verisign.com/com/v1/domain/"),
        "net", List.of("https://rdap.verisign.com/net/v1/domain/"),
        "org", List.of("https://rdap.publicinterestregistry.org/rdap/domain/"),
        "eu", List.of("https://rdap.eu/rdap/domain/")
    );

    @GetMapping(path = "/domain/test", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> test(@RequestParam("domain") String input) {
        String normalized;
        try {
            normalized = normalizeHost(input);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "status", "invalid",
                "message", ex.getMessage()
            ));
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(normalized);
        } catch (UnknownHostException ex) {
            return ResponseEntity.ok(Map.of(
                "ok", false,
                "status", "dns_fail",
                "message", "DNS: il dominio non risulta risolvibile.",
                "details", ex.getMessage()
            ));
        }

        List<String> ips = new ArrayList<>();
        for (InetAddress addr : addresses) {
            if (addr == null) {
                continue;
            }
            if (isPrivateOrLocal(addr)) {
                return ResponseEntity.ok(Map.of(
                    "ok", false,
                    "status", "blocked",
                    "message", "Test bloccato: il dominio risolve verso un IP non pubblico.",
                    "details", addr.getHostAddress()
                ));
            }
            ips.add(addr.getHostAddress());
        }

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        ProbeResult https = probe(client, "https://" + normalized + "/");
        ProbeResult http = https.ok ? null : probe(client, "http://" + normalized + "/");

        boolean ok = https.ok || (http != null && http.ok);
        String status = ok ? "ok" : "http_fail";
        String message = ok
            ? "OK: dominio raggiungibile."
            : "DNS OK, ma non raggiungibile via HTTP/HTTPS.";

        String details = "DNS=" + ips + ", HTTPS=" + https + (http != null ? ", HTTP=" + http : "");

        return ResponseEntity.ok(Map.of(
            "ok", ok,
            "status", status,
            "message", message,
            "details", details,
            "domain", normalized,
            "checkedAt", OffsetDateTime.now().toString()
        ));
    }

    @GetMapping(path = "/domain/availability", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> availability(@RequestParam("domain") String input) {
        String normalized;
        try {
            normalized = normalizeHost(input);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "status", "invalid",
                "message", ex.getMessage()
            ));
        }

        String tld = tldOf(normalized);
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        List<String> endpoints = rdapEndpointsFor(normalized, tld, client);
        List<RdapProbe> probes = new ArrayList<>();

        int availableCount = 0;
        int strongAvailableCount = 0;
        boolean registered = false;
        boolean invalid = false;

        for (int i = 0; i < Math.min(endpoints.size(), 5); i++) {
            String url = endpoints.get(i);
            RdapProbe probe = rdapProbe(client, url);
            probes.add(probe);
            if (probe.classification.equals("registered")) {
                registered = true;
                break;
            }
            if (probe.classification.equals("available")) {
                availableCount++;
                if (!isAggregator(probe.url)) {
                    strongAvailableCount++;
                }
                if (availableCount >= 2) {
                    break;
                }
            }
            if (probe.classification.equals("invalid")) {
                invalid = true;
            }
        }

        if (registered) {
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "status", "registered",
                "message", "Non acquistabile: risulta già registrato.",
                "details", summarizeProbes(probes)
            ));
        }

        DnsDelegation delegation = dnsDelegation(normalized);
        if (delegation.delegated) {
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "status", "likely_registered",
                "message", "Probabilmente già registrato (delega DNS presente).",
                "details", summarizeProbes(probes) + "; " + delegation.details
            ));
        }

        if (strongAvailableCount >= 1 && availableCount >= 2) {
            SanityReachability sanity = sanityReachability(client, normalized);
            if (sanity.reachable) {
                return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "status", "registered",
                    "message", "Non acquistabile: è raggiungibile (quindi registrato).",
                    "details", summarizeProbes(probes) + "; " + sanity.details + "; " + delegation.details
                ));
            }
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "status", "available",
                "message", "Acquistabile (verificato su più provider).",
                "details", summarizeProbes(probes) + "; " + sanity.details + "; " + delegation.details
            ));
        }
        if (strongAvailableCount >= 1 && availableCount == 1 && !invalid) {
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "status", "probably_available",
                "message", "Probabilmente acquistabile (conferma sul provider).",
                "details", summarizeProbes(probes)
            ));
        }
        if (strongAvailableCount == 0 && availableCount >= 2 && !invalid) {
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "status", "probably_available",
                "message", "Probabilmente acquistabile (verifica debole). Conferma sul provider.",
                "details", summarizeProbes(probes) + "; " + delegation.details
            ));
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(normalized);
            if (addresses != null && addresses.length > 0) {
                return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "status", "likely_registered",
                    "message", "Probabilmente già registrato (DNS risolve).",
                    "details", "DNS=" + addresses[0].getHostAddress() + "; " + summarizeProbes(probes)
                ));
            }
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "status", "unknown",
            "message", "Non verificabile in questo momento. Controlla dal provider.",
            "details", summarizeProbes(probes)
        ));
    }

    @GetMapping(path = "/domain/suggest", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<String> suggest(@RequestParam("q") String q) {
        String base = slugify(q);
        if (base.isBlank()) {
            return List.of();
        }

        Set<String> out = new LinkedHashSet<>();
        out.add(base + ".it");
        out.add(base + ".com");
        out.add(base + "-shop.it");
        out.add(base + "-store.it");
        out.add(base + "shop.it");
        out.add(base + "store.it");
        out.add(base + ".online");
        out.add(base + ".store");
        out.add(base + ".shop");
        return out.stream().limit(12).toList();
    }

    private String normalizeHost(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Inserisci un dominio.");
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Inserisci un dominio.");
        }
        if (value.contains("://") || value.contains("/") || value.contains("?") || value.contains("#")) {
            throw new IllegalArgumentException("Inserisci solo il dominio (es. acme.it), senza https:// o percorsi.");
        }
        value = value.replaceAll("\\s+", "");
        value = value.replaceAll("^\\.+|\\.+$", "");
        if (value.equalsIgnoreCase("localhost")) {
            throw new IllegalArgumentException("Dominio non valido.");
        }
        String ascii = IDN.toASCII(value, IDN.ALLOW_UNASSIGNED).toLowerCase();
        if (!HOST_RE.matcher(ascii).matches()) {
            throw new IllegalArgumentException("Dominio non valido. Esempio: acme.it");
        }
        return ascii;
    }

    private boolean isPrivateOrLocal(InetAddress addr) {
        return addr.isAnyLocalAddress()
            || addr.isLoopbackAddress()
            || addr.isLinkLocalAddress()
            || addr.isSiteLocalAddress()
            || addr.isMulticastAddress();
    }

    private ProbeResult probe(HttpClient client, String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            int status = resp.statusCode();
            boolean ok = status >= 200 && status < 500;
            return new ProbeResult(ok, status, null);
        } catch (Exception ex) {
            return new ProbeResult(false, null, ex.getClass().getSimpleName());
        }
    }

    private List<String> rdapEndpointsFor(String domain, String tld, HttpClient client) {
        Set<String> endpoints = new LinkedHashSet<>();
        for (String base : RDAP_FASTPATH.getOrDefault(tld, List.of())) {
            endpoints.add(base + domain);
        }
        for (String base : rdapBasesFromBootstrap(tld, client)) {
            String endpoint = toRdapDomainEndpoint(base, domain);
            if (endpoint != null) {
                endpoints.add(endpoint);
            }
        }
        endpoints.add("https://rdap.org/domain/" + domain);
        return new ArrayList<>(endpoints);
    }

    private List<String> rdapBasesFromBootstrap(String tld, HttpClient client) {
        try {
            long now = System.currentTimeMillis();
            if (!bootstrapByTld.isEmpty() && now - bootstrapLoadedAt < BOOTSTRAP_TTL_MS) {
                return bootstrapByTld.getOrDefault(tld, List.of());
            }
            synchronized (BOOTSTRAP_LOCK) {
                now = System.currentTimeMillis();
                if (!bootstrapByTld.isEmpty() && now - bootstrapLoadedAt < BOOTSTRAP_TTL_MS) {
                    return bootstrapByTld.getOrDefault(tld, List.of());
                }
                Map<String, List<String>> loaded = loadBootstrap(client);
                bootstrapByTld = loaded;
                bootstrapLoadedAt = System.currentTimeMillis();
                return loaded.getOrDefault(tld, List.of());
            }
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Map<String, List<String>> loadBootstrap(HttpClient client) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(IANA_RDAP_BOOTSTRAP)
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .header("User-Agent", "acf-domain-check/1.0")
                .GET()
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return Map.of();
            }
            JsonNode root = JSON.readTree(resp.body());
            JsonNode services = root.get("services");
            if (services == null || !services.isArray()) {
                return Map.of();
            }
            Map<String, List<String>> map = new HashMap<>();
            for (JsonNode entry : services) {
                if (entry == null || !entry.isArray() || entry.size() < 2) {
                    continue;
                }
                JsonNode tldsNode = entry.get(0);
                JsonNode basesNode = entry.get(1);
                if (tldsNode == null || basesNode == null || !tldsNode.isArray() || !basesNode.isArray()) {
                    continue;
                }
                List<String> bases = new ArrayList<>();
                for (JsonNode baseNode : basesNode) {
                    if (baseNode == null || !baseNode.isTextual()) {
                        continue;
                    }
                    String base = baseNode.asText();
                    if (base == null || base.isBlank()) {
                        continue;
                    }
                    bases.add(base);
                }
                if (bases.isEmpty()) {
                    continue;
                }
                for (JsonNode tldNode : tldsNode) {
                    if (tldNode == null || !tldNode.isTextual()) {
                        continue;
                    }
                    String currentTld = tldNode.asText().toLowerCase();
                    if (currentTld.isBlank()) {
                        continue;
                    }
                    map.put(currentTld, Collections.unmodifiableList(bases));
                }
            }
            return Collections.unmodifiableMap(map);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String toRdapDomainEndpoint(String base, String domain) {
        if (base == null || base.isBlank()) {
            return null;
        }
        String normalized = base.trim();
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        if (normalized.contains("/domain/")) {
            return normalized + domain;
        }
        return normalized + "domain/" + domain;
    }

    private RdapProbe rdapProbe(HttpClient client, String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(6))
                .header("Accept", "application/rdap+json, application/json")
                .header("User-Agent", "acf-domain-check/1.0")
                .GET()
                .build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            int status = resp.statusCode();
            if (status == 404) {
                return new RdapProbe(url, status, "available", null);
            }
            if (status >= 200 && status < 300) {
                return new RdapProbe(url, status, "registered", null);
            }
            if (status == 400) {
                return new RdapProbe(url, status, "invalid", null);
            }
            return new RdapProbe(url, status, "unknown", null);
        } catch (Exception ex) {
            return new RdapProbe(url, null, "unknown", ex.getClass().getSimpleName());
        }
    }

    private String summarizeProbes(List<RdapProbe> probes) {
        if (probes == null || probes.isEmpty()) {
            return "Nessun provider RDAP disponibile.";
        }
        StringBuilder sb = new StringBuilder();
        for (RdapProbe probe : probes) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(shortHost(probe.url)).append(":").append(probe.classification);
            if (probe.statusCode != null) {
                sb.append("(").append(probe.statusCode).append(")");
            }
            if (probe.error != null) {
                sb.append("[").append(probe.error).append("]");
            }
        }
        return sb.toString();
    }

    private String shortHost(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host == null ? "rdap" : host;
        } catch (Exception ex) {
            return "rdap";
        }
    }

    private boolean isAggregator(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null && host.toLowerCase().endsWith("rdap.org");
        } catch (Exception ex) {
            return false;
        }
    }

    private SanityReachability sanityReachability(HttpClient client, String domain) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(domain);
            if (addresses == null || addresses.length == 0) {
                return new SanityReachability(false, "Sanity: DNS non risolve");
            }
            List<String> ips = new ArrayList<>();
            for (InetAddress addr : addresses) {
                if (addr == null) {
                    continue;
                }
                if (isPrivateOrLocal(addr)) {
                    return new SanityReachability(false, "Sanity: DNS IP non pubblico (" + addr.getHostAddress() + ")");
                }
                ips.add(addr.getHostAddress());
            }
            ProbeResult https = probe(client, "https://" + domain + "/");
            ProbeResult http = https.ok ? null : probe(client, "http://" + domain + "/");
            boolean reachable = https.ok || (http != null && http.ok);
            String details = "Sanity: DNS=" + ips + ", HTTPS=" + https + (http != null ? ", HTTP=" + http : "");
            return new SanityReachability(reachable, details);
        } catch (Exception ex) {
            return new SanityReachability(false, "Sanity: errore " + ex.getClass().getSimpleName());
        }
    }

    private String tldOf(String domain) {
        int idx = domain.lastIndexOf('.');
        if (idx < 0 || idx == domain.length() - 1) {
            return "";
        }
        return domain.substring(idx + 1).toLowerCase();
    }

    private String slugify(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().toLowerCase();
        value = value.replaceAll("[^a-z0-9]+", "-");
        value = value.replaceAll("(^-+|-+$)", "");
        return value;
    }

    private DnsDelegation dnsDelegation(String domain) {
        if (domain == null || domain.isBlank()) {
            return new DnsDelegation(false, "DNS: dominio vuoto");
        }
        try {
            return CompletableFuture
                .supplyAsync(() -> dnsDelegationBlocking(domain))
                .orTimeout(1500, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> new DnsDelegation(false, "DNS: timeout/errore"))
                .join();
        } catch (Exception ex) {
            return new DnsDelegation(false, "DNS: errore");
        }
    }

    private DnsDelegation dnsDelegationBlocking(String domain) {
        DirContext ctx = null;
        try {
            Hashtable<String, Object> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            env.put(Context.PROVIDER_URL, "dns:");
            env.put("com.sun.jndi.dns.timeout.initial", "1000");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            ctx = new InitialDirContext(env);

            Attributes attrs = ctx.getAttributes(domain, new String[] {"NS", "SOA"});
            Attribute ns = attrs.get("NS");
            Attribute soa = attrs.get("SOA");
            boolean delegated = (ns != null && ns.size() > 0) || (soa != null && soa.size() > 0);
            String details = "DNS: " + (delegated ? "delegato" : "non delegato");
            if (delegated) {
                details += " (NS=" + safeSize(ns) + ", SOA=" + safeSize(soa) + ")";
            }
            return new DnsDelegation(delegated, details);
        } catch (NameNotFoundException ex) {
            return new DnsDelegation(false, "DNS: NXDOMAIN (nessuna delega)");
        } catch (NamingException ex) {
            return new DnsDelegation(false, "DNS: errore resolver");
        } catch (Exception ex) {
            return new DnsDelegation(false, "DNS: errore");
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private int safeSize(Attribute attr) {
        try {
            return attr == null ? 0 : attr.size();
        } catch (Exception ex) {
            return 0;
        }
    }

    private record DnsDelegation(boolean delegated, String details) {}

    private record ProbeResult(boolean ok, Integer status, String error) {
        @Override
        public String toString() {
            if (ok) {
                return "ok(" + status + ")";
            }
            if (status != null) {
                return "fail(" + status + ")";
            }
            return "err(" + error + ")";
        }
    }

    private record RdapProbe(String url, Integer statusCode, String classification, String error) {
        RdapProbe {
            url = Objects.requireNonNullElse(url, "");
            classification = Objects.requireNonNullElse(classification, "unknown");
        }
    }

    private record SanityReachability(boolean reachable, String details) {}
}
