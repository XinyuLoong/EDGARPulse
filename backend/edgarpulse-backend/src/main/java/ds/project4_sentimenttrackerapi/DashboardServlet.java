package ds.project4_sentimenttrackerapi;

/**
 * Author: Xinyu Long
 * File: DashboardServlet.java
 * Description: the logic for the dashboard page:
 * displays analytics and visualizations based on the API request logs
 * stored in MongoDB. It retrieves all logs, computes various statistics
 * and forwards the data to the JSP page for rendering.
 */

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "DashboardServlet", urlPatterns = {"/dashboard"})
public class DashboardServlet extends HttpServlet {

    private static final String MONGO_CONNECTION_URL = getRequiredEnv("MONGO_CONNECTION_URL");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        List<Document> allLogs = new ArrayList<>(); // To store all logs for display
        int totalRequests = 0; // Total number of API requests
        int elevatedRiskCount = 0; // Count of requests with elevated risk (riskToneScore >= 65)
        int highActivityCount = 0; // Count of requests with high activity level
        int successCount = 0; // Count of successful API requests

        double totalRiskToneScore = 0.0; // Sum of all risk tone scores for calculating average
        int riskScoreCount = 0; // Count of valid risk tone scores for calculating average

        double totalProcessingTimeMs = 0.0; // Sum of all processing times for calculating average
        int processingTimeCount = 0; // Count of valid processing times for calculating average

        Map<String, Integer> companyFrequency = new HashMap<>(); // Frequency map for company names
        Map<String, Integer> formTypeFrequency = new HashMap<>(); // Frequency map for form types
        Map<String, Integer> riskLabelFrequency = new HashMap<>(); // Frequency map for risk tone labels

        String mostSearchedCompany = "N/A"; // Most frequently searched company
        String mostCommonFormType = "N/A"; // Most common form type analyzed
        String averageRiskToneScore = "N/A"; // Average risk tone score across all requests
        String averageProcessingTimeMs = "N/A"; // Average processing time in milliseconds across all requests

        // Connect to MongoDB and retrieve logs
        try (MongoClient mongoClient = MongoClients.create(MONGO_CONNECTION_URL)) {

            MongoDatabase database = mongoClient.getDatabase("edgarpulse_db");
            MongoCollection<Document> collection = database.getCollection("api_logs");

            for (Document doc : collection.find()) {
                allLogs.add(doc);
                totalRequests++;

                // Success count
                Boolean success = getBooleanValue(doc, "success");
                if (Boolean.TRUE.equals(success)) {
                    successCount++;
                }

                // Risk score analytics
                Integer riskScore = getIntegerValue(doc, "riskToneScore");
                if (riskScore != null && riskScore >= 0) {
                    totalRiskToneScore += riskScore;
                    riskScoreCount++;

                    if (riskScore >= 65) {
                        elevatedRiskCount++;
                    }
                }

                // Risk label distribution
                String riskLabel = getStringValue(doc, "riskToneLabel");
                if (riskLabel != null && !riskLabel.isBlank() && !"N/A".equalsIgnoreCase(riskLabel)) {
                    riskLabelFrequency.put(riskLabel, riskLabelFrequency.getOrDefault(riskLabel, 0) + 1);
                }

                // Activity analytics
                String activityLevel = getStringValue(doc, "activityLevel");
                if ("High".equalsIgnoreCase(activityLevel)) {
                    highActivityCount++;
                }

                // Company frequency
                String companyName = getStringValue(doc, "companyName");
                if (companyName != null && !companyName.isBlank() && !"N/A".equalsIgnoreCase(companyName)) {
                    companyFrequency.put(companyName, companyFrequency.getOrDefault(companyName, 0) + 1);
                }

                // Form type frequency
                String formType = getStringValue(doc, "analyzedFormType");
                if (formType != null && !formType.isBlank() && !"N/A".equalsIgnoreCase(formType)) {
                    formTypeFrequency.put(formType, formTypeFrequency.getOrDefault(formType, 0) + 1);
                }

                // Processing time analytics
                Integer processingTime = getIntegerValue(doc, "processingTimeMs");
                if (processingTime != null && processingTime >= 0) {
                    totalProcessingTimeMs += processingTime;
                    processingTimeCount++;
                }
            }

            mostSearchedCompany = findMostFrequentEntry(companyFrequency);
            mostCommonFormType = findMostFrequentEntry(formTypeFrequency);

            if (riskScoreCount > 0) {
                averageRiskToneScore = String.format("%.2f", totalRiskToneScore / riskScoreCount);
            }

            if (processingTimeCount > 0) {
                averageProcessingTimeMs = String.format("%.2f", totalProcessingTimeMs / processingTimeCount);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("MongoDB connection failed: " + e.getMessage());
        }

        request.setAttribute("logs", allLogs);
        request.setAttribute("totalRequests", totalRequests);
        request.setAttribute("elevatedRiskCount", elevatedRiskCount);
        request.setAttribute("highActivityCount", highActivityCount);
        request.setAttribute("successCount", successCount);
        request.setAttribute("averageRiskToneScore", averageRiskToneScore);
        request.setAttribute("averageProcessingTimeMs", averageProcessingTimeMs);
        request.setAttribute("mostSearchedCompany", mostSearchedCompany);
        request.setAttribute("mostCommonFormType", mostCommonFormType);
        request.setAttribute("riskLabelFrequency", riskLabelFrequency);

        request.getRequestDispatcher("/dashboard.jsp").forward(request, response);
    }

    //---------------------------------------------------------------------
    //                    Helpter Functions
    //---------------------------------------------------------------------

    // find the most frequent entry in a frequency map
    private String findMostFrequentEntry(Map<String, Integer> frequencyMap) {
        String result = "N/A";
        int maxCount = 0;

        for (Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                result = entry.getKey() + " (" + maxCount + " times)";
            }
        }

        return result;
    }

    // safely extract typed values from MongoDB documents
    private String getStringValue(Document doc, String key) {
        Object value = doc.get(key);
        return value == null ? null : value.toString();
    }

    // extract an integer value from the document(of different possible types)
    private Integer getIntegerValue(Document doc, String key) {
        Object value = doc.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long) {
            return ((Long) value).intValue();
        }
        if (value instanceof Double) {
            return ((Double) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    // extract a boolean value from the document (of different possible types)
    private Boolean getBooleanValue(Document doc, String key) {
        Object value = doc.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return null;
    }

    // get required environment variables
    private static String getRequiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }
}