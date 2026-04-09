package ds.project4_sentimenttrackerapi;

/**
 * Author: Xinyu Long (Rey)
 * File: RiskToneAnalysisServlet.java
 * Description: Has the following functionalities:
 * 1. Validates the cik query parameter to ensure it is present and properly formatted
 * 2. Fetches company and filing data from the SEC EDGAR API using the provided CIK
 * 3. Analyzes the most recent filings to determine risk tone and filing activity level based on simple heuristics.
 * 4. Constructs a JSON response containing the analysis results, including risk tone score, risk tone label, activity level, and a summary.
 */

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerApi;
import com.mongodb.ServerApiVersion;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.Document;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

@WebServlet(name = "RiskToneAnalysisServlet", urlPatterns = {"/analyze"})
public class RiskToneAnalysisServlet extends HttpServlet {

    private static final String MONGO_CONNECTION_URL = getRequiredEnv("MONGO_CONNECTION_URL");
    private static final String SEC_USER_AGENT = getRequiredEnv("SEC_USER_AGENT");

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient(); // Shared HttpClient instance for making HTTP requests
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>"); // stripping HTML tags

    // simple dictionaries for risk tone analysis
    private static final Set<String> RISK_WORDS = Set.of(
            "risk", "risks", "uncertainty", "uncertain", "adverse", "volatility",
            "volatile", "exposure", "decline", "deterioration", "decrease",
            "challenging", "challenge", "pressure", "pressures", "weakness", "weak"
    );

    private static final Set<String> LEGAL_WORDS = Set.of(
            "litigation", "investigation", "regulatory", "regulation", "noncompliance",
            "penalty", "penalties", "violation", "violations", "enforcement", "lawsuit",
            "lawsuits", "compliance"
    );

    private static final Set<String> DISTRESS_WORDS = Set.of(
            "impairment", "restatement", "default", "defaults", "going", "concern",
            "liquidity", "material", "weakness", "bankruptcy", "loss", "losses"
    );

    private static final Set<String> POSITIVE_WORDS = Set.of(
            "improvement", "improved", "effective", "growth", "strengthened", "resolved",
            "stable", "profit", "profitable", "opportunity", "opportunities", "success",
            "successful", "benefit", "benefits"
    );

    // Main entry point for GET requests to the /analyze endpoint
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        long totalStartTime = System.currentTimeMillis();

        String rawCik = request.getParameter("cik");
        String clientIp = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        boolean success = false;
        String errorMessage = null;

        String normalizedCik = null;
        String companyName = "N/A";
        String industry = "N/A";
        String ticker = "N/A";
        String analyzedFormType = "N/A";
        String filingDate = "N/A";

        int secHttpStatus = -1;
        long secLatencyMs = -1;
        int secResponseLengthBytes = 0;

        int riskToneScore = -1;
        String riskToneLabel = "N/A";
        String activityLevel = "N/A";
        int recentFilingCount = 0;
        int amendmentCount = 0;
        int eightKCount = 0;
        long daysSinceLastFiling = -1;
        String summary = "N/A";

        String secRequestUrl = null;

        try {
            // Validate: cik must be present, numeric, and can have leading zeros
            if (rawCik == null || rawCik.trim().isEmpty()) {
                writeErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing 'cik' parameter");
                return;
            }
            rawCik = rawCik.trim();
            if (!rawCik.matches("\\d+")) {
                writeErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "CIK must contain digits only");
                return;
            }

            normalizedCik = String.format("%010d", Long.parseLong(rawCik));

            // SEC API
            secRequestUrl = "https://data.sec.gov/submissions/CIK" + normalizedCik + ".json";

            long secStart = System.currentTimeMillis();
            HttpRequest secRequest = HttpRequest.newBuilder()
                    .uri(URI.create(secRequestUrl))
                    .header("User-Agent", SEC_USER_AGENT)
                    .header("User-Agent", "Xinyu Long xinyulon@andrew.cmu.edu")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> secResponse = HTTP_CLIENT.send(secRequest, HttpResponse.BodyHandlers.ofString());
            secLatencyMs = System.currentTimeMillis() - secStart;
            secHttpStatus = secResponse.statusCode();

            if (secResponse.statusCode() != 200) {
                errorMessage = "SEC API returned status " + secResponse.statusCode();
                writeErrorResponse(response, HttpServletResponse.SC_BAD_GATEWAY, errorMessage);
                return;
            }

            String secJson = secResponse.body();
            secResponseLengthBytes = secJson.getBytes(StandardCharsets.UTF_8).length;

            JsonObject root = JsonParser.parseString(secJson).getAsJsonObject();

            companyName = getSafeString(root, "name");
            industry = getSafeString(root, "sicDescription");

            if (root.has("tickers") && root.get("tickers").isJsonArray()) {
                JsonArray tickers = root.getAsJsonArray("tickers");
                if (tickers.size() > 0) {
                    ticker = tickers.get(0).getAsString();
                }
            }

            // Parse recent filings
            if (!root.has("filings")) {
                errorMessage = "SEC response missing filings object";
                writeErrorResponse(response, HttpServletResponse.SC_BAD_GATEWAY, errorMessage);
                return;
            }

            JsonObject filings = root.getAsJsonObject("filings");
            if (!filings.has("recent")) {
                errorMessage = "SEC response missing recent filings";
                writeErrorResponse(response, HttpServletResponse.SC_BAD_GATEWAY, errorMessage);
                return;
            }

            JsonObject recent = filings.getAsJsonObject("recent");
            JsonArray forms = recent.getAsJsonArray("form");
            JsonArray filingDates = recent.getAsJsonArray("filingDate");
            JsonArray accessionNumbers = recent.getAsJsonArray("accessionNumber");
            JsonArray primaryDocuments = recent.getAsJsonArray("primaryDocument");

            List<FilingRecord> recentFilings = new ArrayList<>();
            int total = forms.size();

            for (int i = 0; i < total; i++) {
                String form = getArrayString(forms, i);
                String date = getArrayString(filingDates, i);
                String accession = getArrayString(accessionNumbers, i);
                String primaryDoc = getArrayString(primaryDocuments, i);

                if (form == null || date == null || accession == null || primaryDoc == null) {
                    continue;
                }

                recentFilings.add(new FilingRecord(form, date, accession, primaryDoc));
            }

            if (recentFilings.isEmpty()) {
                errorMessage = "No recent filings found for this company";
                writeErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, errorMessage);
                return;
            }

            // Filing activity stats for recent 365 days
            LocalDate today = LocalDate.now();
            LocalDate oneYearAgo = today.minusDays(365);

            for (FilingRecord filing : recentFilings) {
                LocalDate fd;
                try {
                    fd = LocalDate.parse(filing.filingDate);
                } catch (Exception e) {
                    continue;
                }

                if (!fd.isBefore(oneYearAgo)) {
                    recentFilingCount++;

                    if (filing.form.endsWith("/A")) {
                        amendmentCount++;
                    }

                    if ("8-K".equalsIgnoreCase(filing.form) || "8-K/A".equalsIgnoreCase(filing.form)) {
                        eightKCount++;
                    }
                }
            }

            FilingRecord latestFiling = recentFilings.get(0);
            try {
                daysSinceLastFiling = ChronoUnit.DAYS.between(LocalDate.parse(latestFiling.filingDate), today);
            } catch (Exception ignored) {
                daysSinceLastFiling = -1;
            }

            activityLevel = determineActivityLevel(recentFilingCount, amendmentCount, eightKCount, daysSinceLastFiling);

            // Pick one filing for text-based tone analysis
            FilingRecord targetFiling = selectBestFiling(recentFilings);
            if (targetFiling == null) {
                errorMessage = "No suitable filing found for analysis";
                writeErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, errorMessage);
                return;
            }

            analyzedFormType = targetFiling.form;
            filingDate = targetFiling.filingDate;

            // Fetch filing document text
            String filingText = fetchFilingText(normalizedCik, targetFiling);
            if (filingText == null || filingText.isBlank()) {
                errorMessage = "Could not retrieve filing text for analysis";
                writeErrorResponse(response, HttpServletResponse.SC_BAD_GATEWAY, errorMessage);
                return;
            }

            // Analyze risk tone
            RiskToneResult toneResult = analyzeRiskTone(filingText);
            riskToneScore = toneResult.score;
            riskToneLabel = toneResult.label;

            // summary
            summary = buildSummary(riskToneLabel, activityLevel, recentFilingCount, amendmentCount, eightKCount);

            // JSON for Android
            JsonObject mobileResponse = new JsonObject();
            mobileResponse.addProperty("success", true);
            mobileResponse.addProperty("companyName", companyName);
            mobileResponse.addProperty("industry", industry);
            mobileResponse.addProperty("ticker", ticker);
            mobileResponse.addProperty("analyzedFormType", analyzedFormType);
            mobileResponse.addProperty("filingDate", filingDate);
            mobileResponse.addProperty("riskToneScore", riskToneScore);
            mobileResponse.addProperty("riskToneLabel", riskToneLabel);
            mobileResponse.addProperty("activityLevel", activityLevel);
            mobileResponse.addProperty("recentFilingCount", recentFilingCount);
            mobileResponse.addProperty("amendmentCount", amendmentCount);
            mobileResponse.addProperty("eightKCount", eightKCount);
            mobileResponse.addProperty("daysSinceLastFiling", daysSinceLastFiling);
            mobileResponse.addProperty("summary", summary);

            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            PrintWriter out = response.getWriter();
            out.print(new Gson().toJson(mobileResponse));
            out.flush();

            success = true;

        } catch (Exception e) {
            errorMessage = e.getMessage();
            e.printStackTrace();
            writeErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
        } finally {
            long processingTimeMs = System.currentTimeMillis() - totalStartTime;
            // for dashboard visualization and auditing
            logToMongoDB(
                    clientIp,
                    userAgent,
                    rawCik,
                    normalizedCik,
                    secRequestUrl,
                    secHttpStatus,
                    secLatencyMs,
                    secResponseLengthBytes,
                    companyName,
                    industry,
                    analyzedFormType,
                    filingDate,
                    riskToneScore,
                    riskToneLabel,
                    activityLevel,
                    recentFilingCount,
                    amendmentCount,
                    eightKCount,
                    daysSinceLastFiling,
                    summary,
                    success,
                    errorMessage,
                    processingTimeMs
            );
        }
    }

    // Select the best filing for analysis based on form type priority: 10-K > 10-Q > 8-K > others
    private FilingRecord selectBestFiling(List<FilingRecord> filings) {
        for (FilingRecord f : filings) {
            if ("10-K".equalsIgnoreCase(f.form) || "10-K/A".equalsIgnoreCase(f.form)) {
                return f;
            }
        }
        for (FilingRecord f : filings) {
            if ("10-Q".equalsIgnoreCase(f.form) || "10-Q/A".equalsIgnoreCase(f.form)) {
                return f;
            }
        }
        for (FilingRecord f : filings) {
            if ("8-K".equalsIgnoreCase(f.form) || "8-K/A".equalsIgnoreCase(f.form)) {
                return f;
            }
        }
        return filings.isEmpty() ? null : filings.get(0);
    }

    // Fetch the filing document text from SEC EDGAR, given the normalized CIK and filing record
    private String fetchFilingText(String normalizedCik, FilingRecord filing) {
        try {
            String cikWithoutLeadingZeros = String.valueOf(Long.parseLong(normalizedCik));
            String accessionNoDashes = filing.accessionNumber.replace("-", "");
            String encodedPrimaryDoc = URLEncoder.encode(filing.primaryDocument, StandardCharsets.UTF_8);

            String filingUrl = "https://www.sec.gov/Archives/edgar/data/"
                    + cikWithoutLeadingZeros + "/"
                    + accessionNoDashes + "/"
                    + encodedPrimaryDoc;

            HttpRequest filingRequest = HttpRequest.newBuilder()
                    .uri(URI.create(filingUrl))
                    .header("User-Agent", "Xinyu Long xinyulon@andrew.cmu.edu")
                    .header("Accept", "text/html, text/plain, */*")
                    .GET()
                    .build();

            HttpResponse<String> filingResponse = HTTP_CLIENT.send(filingRequest, HttpResponse.BodyHandlers.ofString());

            if (filingResponse.statusCode() != 200) {
                return null;
            }

            String raw = filingResponse.body();
            if (raw == null || raw.isBlank()) {
                return null;
            }

            return stripHtml(raw);
        } catch (Exception e) {
            return null;
        }
    }

    // Analyze the risk tone of the filing text using a simple word-based scoring system
    private RiskToneResult analyzeRiskTone(String filingText) {
        String cleaned = filingText.toLowerCase(Locale.US)
                .replaceAll("[^a-z\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.isBlank()) {
            return new RiskToneResult(50, "Moderate Risk Tone");
        }

        String[] words = cleaned.split("\\s+");
        int wordCount = words.length;

        int riskCount = 0;
        int legalCount = 0;
        int distressCount = 0;
        int positiveCount = 0;

        for (String word : words) {
            if (RISK_WORDS.contains(word)) riskCount++;
            if (LEGAL_WORDS.contains(word)) legalCount++;
            if (DISTRESS_WORDS.contains(word)) distressCount++;
            if (POSITIVE_WORDS.contains(word)) positiveCount++;
        }

        double rawRisk = (1.0 * riskCount)
                + (1.5 * legalCount)
                + (2.0 * distressCount)
                - (0.8 * positiveCount);

        double normalized = rawRisk / Math.max(wordCount, 1) * 1000.0;
        int score = (int) Math.round(50 + normalized);

        if (score < 0) score = 0;
        if (score > 100) score = 100;

        String label;
        if (score >= 65) {
            label = "Elevated Risk Tone";
        } else if (score >= 35) {
            label = "Moderate Risk Tone";
        } else {
            label = "Low Risk Tone";
        }

        return new RiskToneResult(score, label);
    }

    // Determine activity level based on filing frequency, amendments, 8-Ks, and recency
    private String determineActivityLevel(int recentFilingCount, int amendmentCount, int eightKCount, long daysSinceLastFiling) {
        int score = 0;

        if (recentFilingCount > 15) score += 2;
        else if (recentFilingCount >= 6) score += 1;

        if (amendmentCount >= 3) score += 1;
        if (eightKCount >= 5) score += 1;

        if (daysSinceLastFiling >= 0 && daysSinceLastFiling <= 30) score += 1;
        if (daysSinceLastFiling > 120) score -= 1;

        if (score >= 3) return "High";
        if (score >= 1) return "Moderate";
        return "Low";
    }

    // Build a summary string based on the risk tone and activity level
    private String buildSummary(String riskToneLabel, String activityLevel, int recentFilingCount, int amendmentCount, int eightKCount) {
        return riskToneLabel
                + " with "
                + activityLevel.toLowerCase(Locale.US)
                + " disclosure activity. Recent filings: "
                + recentFilingCount
                + ", amendments: "
                + amendmentCount
                + ", 8-K filings: "
                + eightKCount
                + ".";
    }

    // Strip HTML tags and decode common entities to get cleaner text for analysis
    private String stripHtml(String rawHtml) {
        return HTML_TAG_PATTERN.matcher(rawHtml)
                .replaceAll(" ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // Safely get a string property from a JsonObject, returning "N/A" if missing or invalid
    private String getSafeString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return "N/A";
        JsonElement e = obj.get(key);
        if (e == null || e.isJsonNull()) return "N/A";
        try {
            return e.getAsString();
        } catch (Exception ex) {
            return "N/A";
        }
    }

    // Safely get a string from a JsonArray at a given index, returning null if missing or invalid
    private String getArrayString(JsonArray arr, int idx) {
        try {
            JsonElement e = arr.get(idx);
            if (e == null || e.isJsonNull()) return null;
            return e.getAsString();
        } catch (Exception ex) {
            return null;
        }
    }

    // Write an error response in JSON format with a given HTTP status and message
    private void writeErrorResponse(HttpServletResponse response, int httpStatus, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        JsonObject err = new JsonObject();
        err.addProperty("success", false);
        err.addProperty("error", message);

        response.getWriter().write(new Gson().toJson(err));
    }

    // Log the request and analysis results to MongoDB for dashboard visualization and auditing
    private void logToMongoDB(
            String clientIp,
            String userAgent,
            String rawCik,
            String normalizedCik,
            String secRequestUrl,
            int secHttpStatus,
            long secLatencyMs,
            int secResponseLengthBytes,
            String companyName,
            String industry,
            String analyzedFormType,
            String filingDate,
            int riskToneScore,
            String riskToneLabel,
            String activityLevel,
            int recentFilingCount,
            int amendmentCount,
            int eightKCount,
            long daysSinceLastFiling,
            String summary,
            boolean success,
            String errorMessage,
            long processingTimeMs
    ) {
        ServerApi serverApi = ServerApi.builder().version(ServerApiVersion.V1).build();
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(MONGO_CONNECTION_URL))
                .serverApi(serverApi)
                .build();

        try (MongoClient mongoClient = MongoClients.create(settings)) {
            MongoDatabase database = mongoClient.getDatabase("edgarpulse_db");
            MongoCollection<Document> collection = database.getCollection("api_logs");

            Document logEntry = new Document("timestamp", new Date())
                    .append("clientIp", clientIp)
                    .append("userAgent", userAgent)
                    .append("rawCik", rawCik)
                    .append("normalizedCik", normalizedCik)
                    .append("secRequestUrl", secRequestUrl)
                    .append("secHttpStatus", secHttpStatus)
                    .append("secLatencyMs", secLatencyMs)
                    .append("secResponseLengthBytes", secResponseLengthBytes)
                    .append("companyName", companyName)
                    .append("industry", industry)
                    .append("analyzedFormType", analyzedFormType)
                    .append("filingDate", filingDate)
                    .append("riskToneScore", riskToneScore)
                    .append("riskToneLabel", riskToneLabel)
                    .append("activityLevel", activityLevel)
                    .append("recentFilingCount", recentFilingCount)
                    .append("amendmentCount", amendmentCount)
                    .append("eightKCount", eightKCount)
                    .append("daysSinceLastFiling", daysSinceLastFiling)
                    .append("summary", summary)
                    .append("success", success)
                    .append("errorMessage", errorMessage)
                    .append("processingTimeMs", processingTimeMs);

            collection.insertOne(logEntry);
            System.out.println("Log successfully written to MongoDB");
        } catch (Exception e) {
            System.err.println("Failed to write log to MongoDB: " + e.getMessage());
        }
    }

    // Simple data class to hold filing information for easier handling
    private static class FilingRecord {
        String form;
        String filingDate;
        String accessionNumber;
        String primaryDocument;

        FilingRecord(String form, String filingDate, String accessionNumber, String primaryDocument) {
            this.form = form;
            this.filingDate = filingDate;
            this.accessionNumber = accessionNumber;
            this.primaryDocument = primaryDocument;
        }
    }

    // Simple data class to hold risk tone analysis results
    private static class RiskToneResult {
        int score;
        String label;

        RiskToneResult(int score, String label) {
            this.score = score;
            this.label = label;
        }
    }

    // get required environment variables, throwing an exception if missing or blank
    private static String getRequiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }
}