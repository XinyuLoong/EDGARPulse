<%--
Author: Xinyu Long
File: dashboard.jsp
Description: This JSP page serves as the dashboard for the SEC Filing Risk Tone
Analyzer. It displays key analytics about API usage and a formatted table of
recent logs from the MongoDB collection.
--%>

<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List" %>
<%@ page import="org.bson.Document" %>

<html>
<head>
    <title>SEC Filing Risk Tone Dashboard</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            margin: 32px;
            background-color: #f7f9fc;
            color: #222;
        }

        h1 {
            color: #1E88E5;
            margin-bottom: 8px;
        }

        h2 {
            color: #333;
            margin-top: 32px;
            margin-bottom: 16px;
        }

        .subtitle {
            color: #666;
            margin-bottom: 28px;
        }

        .analytics-card {
            background: white;
            padding: 20px 24px;
            border-radius: 10px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.08);
            margin-bottom: 28px;
        }

        .analytics-card ul {
            margin: 0;
            padding-left: 20px;
        }

        .analytics-card li {
            margin-bottom: 10px;
            line-height: 1.5;
        }

        table {
            width: 100%;
            border-collapse: collapse;
            background: white;
            box-shadow: 0 2px 8px rgba(0,0,0,0.08);
            border-radius: 10px;
            overflow: hidden;
            font-size: 14px;
        }

        th, td {
            padding: 12px 10px;
            text-align: left;
            vertical-align: top;
            border-bottom: 1px solid #e6e6e6;
        }

        th {
            background-color: #1E88E5;
            color: white;
            font-weight: bold;
        }

        tr:nth-child(even) {
            background-color: #fafafa;
        }

        .risk-low {
            color: #388E3C;
            font-weight: bold;
        }

        .risk-moderate {
            color: #F57C00;
            font-weight: bold;
        }

        .risk-high {
            color: #D32F2F;
            font-weight: bold;
        }

        .status-success {
            color: #388E3C;
            font-weight: bold;
        }

        .status-failed {
            color: #D32F2F;
            font-weight: bold;
        }

        .muted {
            color: #777;
        }
    </style>
</head>
<body>

<h1>SEC Filing Risk Tone Dashboard</h1>
<p class="subtitle">Operations analytics and formatted logs for the mobile-to-cloud SEC filing analyzer.</p>

<div class="analytics-card">
    <h2>Record</h2>
    <ul>
        <li><strong>Total API Requests:</strong> <%= request.getAttribute("totalRequests") %></li>
        <li><strong>Most Queried Company:</strong> <%= request.getAttribute("mostSearchedCompany") %></li>
        <li><strong>Most Common Analyzed Form:</strong> <%= request.getAttribute("mostCommonFormType") %></li>
        <li><strong>Average Risk Tone Score:</strong> <%= request.getAttribute("averageRiskToneScore") %></li>
        <li><strong>Elevated Risk Tone Requests:</strong> <%= request.getAttribute("elevatedRiskCount") %></li>
        <li><strong>High Disclosure Activity Requests:</strong> <%= request.getAttribute("highActivityCount") %></li>
        <li><strong>Successful Requests:</strong> <%= request.getAttribute("successCount") %></li>
        <li><strong>Average Processing Time:</strong> <%= request.getAttribute("averageProcessingTimeMs") %> ms</li>
    </ul>
</div>

<h2>Formatted Full Logs</h2>
<table>
    <thead>
    <tr>
        <th>Log ID</th>
        <th>Timestamp</th>
        <th>CIK</th>
        <th>Company</th>
        <th>Form Type</th>
        <th>Risk Score</th>
        <th>Risk Label</th>
        <th>Activity</th>
        <th>Recent Filings</th>
        <th>Amendments</th>
        <th>8-K Count</th>
        <th>Processing Time</th>
        <th>Status</th>
    </tr>
    </thead>
    <tbody>
    <%
        List<Document> logs = (List<Document>) request.getAttribute("logs");
        if (logs != null && !logs.isEmpty()) {
            for (Document doc : logs) {
                Object riskLabelObj = doc.get("riskToneLabel");
                String riskLabel = riskLabelObj != null ? riskLabelObj.toString() : "N/A";

                String riskCssClass = "muted";
                if ("Low Risk Tone".equalsIgnoreCase(riskLabel)) {
                    riskCssClass = "risk-low";
                } else if ("Moderate Risk Tone".equalsIgnoreCase(riskLabel)) {
                    riskCssClass = "risk-moderate";
                } else if ("Elevated Risk Tone".equalsIgnoreCase(riskLabel)) {
                    riskCssClass = "risk-high";
                }

                Object successObj = doc.get("success");
                boolean success = successObj instanceof Boolean && (Boolean) successObj;
                String statusText = success ? "Success" : "Failed";
                String statusCssClass = success ? "status-success" : "status-failed";
    %>
    <tr>
        <td><%= doc.getObjectId("_id") != null ? doc.getObjectId("_id").toString() : "N/A" %></td>
        <td><%= doc.get("timestamp") != null ? doc.get("timestamp").toString() : "N/A" %></td>
        <td><%= doc.get("normalizedCik") != null ? doc.get("normalizedCik").toString() : "N/A" %></td>
        <td><%= doc.get("companyName") != null ? doc.get("companyName").toString() : "N/A" %></td>
        <td><%= doc.get("analyzedFormType") != null ? doc.get("analyzedFormType").toString() : "N/A" %></td>
        <td><%= doc.get("riskToneScore") != null ? doc.get("riskToneScore").toString() : "N/A" %></td>
        <td class="<%= riskCssClass %>"><%= riskLabel %></td>
        <td><%= doc.get("activityLevel") != null ? doc.get("activityLevel").toString() : "N/A" %></td>
        <td><%= doc.get("recentFilingCount") != null ? doc.get("recentFilingCount").toString() : "N/A" %></td>
        <td><%= doc.get("amendmentCount") != null ? doc.get("amendmentCount").toString() : "N/A" %></td>
        <td><%= doc.get("eightKCount") != null ? doc.get("eightKCount").toString() : "N/A" %></td>
        <td>
            <%= doc.get("processingTimeMs") != null ? doc.get("processingTimeMs").toString() + " ms" : "N/A" %>
        </td>
        <td class="<%= statusCssClass %>"><%= statusText %></td>
    </tr>
    <%
        }
    } else {
    %>
    <tr>
        <td colspan="13" style="text-align:center;">No logs found.</td>
    </tr>
    <%
        }
    %>
    </tbody>
</table>

</body>
</html>